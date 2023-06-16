// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.modcommand.*;
import com.intellij.modcommand.ModUpdateFileText.Fragment;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.NotNullFunction;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class ModCommandServiceImpl implements ModCommandService {
  @Override
  @NotNull
  public IntentionAction wrap(@NotNull ModCommandAction action) {
    return new ModCommandActionWrapper(action);
  }

  @Override
  public @NotNull LocalQuickFix wrapToQuickFix(@NotNull ModCommandAction action) {
    return new ModCommandActionQuickFixWrapper(action);
  }

  @Override
  public @Nullable ModCommandAction unwrap(@NotNull LocalQuickFix fix) {
    if (fix instanceof ModCommandActionQuickFixWrapper wrapper) {
      return wrapper.getAction();
    }
    return null;
  }

  @Override
  @Nullable
  public ModCommandAction unwrap(@NotNull IntentionAction action) {
    while (action instanceof IntentionActionDelegate delegate) {
      action = delegate.getDelegate();
    }
    return action instanceof ModCommandActionWrapper wrapper ? wrapper.action() : null;
  }

  @RequiresEdt
  @Override
  public void execute(@NotNull Project project, @NotNull ModCommand command) {
    Set<VirtualFile> files = command.modifiedFiles();
    if (!files.isEmpty()) {
      if (!ReadonlyStatusHandler.ensureFilesWritable(project, files.toArray(VirtualFile.EMPTY_ARRAY))) {
        return;
      }
    }
    doExecute(project, command);
  }

  private boolean doExecute(@NotNull Project project, @NotNull ModCommand command) {
    if (command instanceof ModUpdateFileText upd) {
      return executeUpdate(project, upd);
    }
    if (command instanceof ModCompositeCommand cmp) {
      return executeComposite(project, cmp);
    }
    if (command instanceof ModNavigate nav) {
      return executeNavigate(project, nav);
    }
    if (command instanceof ModHighlight highlight) {
      return executeHighlight(project, highlight);
    }
    if (command instanceof ModNothing) {
      return true;
    }
    if (command instanceof ModChooseTarget<?> cht) {
      return executeChoose(project, cht);
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  private static <T extends @NotNull PsiElement> boolean executeChoose(@NotNull Project project, ModChooseTarget<@NotNull T> cht) {
    String name = CommandProcessor.getInstance().getCurrentCommandName();
    var elements = cht.elements();
    var nextStep = cht.nextStep();
    if (elements.isEmpty()) return false;
    T element = elements.get(0).element();
    if (elements.size() == 1) {
      executeNextStep(project, nextStep, element, name);
      return true;
    }
    VirtualFile file = element.getContainingFile().getVirtualFile();
    if (file == null) return false;
    Editor editor = getEditor(project, file);
    if (editor == null) return false;
    var map = StreamEx.of(elements).toMap(ModChooseTarget.ListItem::element, Function.identity());
    List<T> elementList = ContainerUtil.map(elements, ModChooseTarget.ListItem::element);
    Pass<T> callback = new Pass<>() {
      @Override
      public void pass(T t) {
        executeNextStep(project, cht.nextStep(), t, name);
      }
    };
    NotNullFunction<PsiElement, TextRange> ranger = e -> map.get(e).selection();
    IntroduceTargetChooser.showChooser(editor, elementList, callback,
                                       e -> map.get(e).toString(), cht.title(), cht.selection(),
                                       ranger);
    return true;
  }

  private static <T extends @NotNull PsiElement> void executeNextStep(@NotNull Project project,
                                                                      Function<? super T, ? extends ModCommand> nextStep,
                                                                      T element, 
                                                                      @Nullable @NlsContexts.Command String name) {
    ReadAction.nonBlocking(() -> nextStep.apply(element))
      .finishOnUiThread(ModalityState.defaultModalityState(), next -> {
        if (name != null) {
          CommandProcessor.getInstance().executeCommand(project, () -> next.execute(project), name, null);
        } else {
          next.execute(project);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static boolean executeNavigate(@NotNull Project project, ModNavigate nav) {
    VirtualFile file = nav.file();
    int selectionStart = nav.selectionStart();
    int selectionEnd = nav.selectionEnd();
    int caret = nav.caret();

    Editor editor = getEditor(project, file);
    if (editor == null) return false;
    if (selectionStart != -1 && selectionEnd != -1) {
      editor.getSelectionModel().setSelection(selectionStart, selectionEnd);
    }
    if (caret != -1) {
      editor.getCaretModel().moveToOffset(caret);
    }
    return true;
  }

  private static boolean executeHighlight(Project project, ModHighlight highlight) {
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(highlight.virtualFile());
    if (!(fileEditor instanceof TextEditor textEditor)) return false;
    Editor editor = textEditor.getEditor();
    HighlightManager manager = HighlightManager.getInstance(project);
    for (ModHighlight.HighlightInfo info : highlight.highlights()) {
      manager.addRangeHighlight(editor, info.range().getStartOffset(), info.range().getEndOffset(), info.attributesKey(),
                                 info.hideByTextChange(), null);
    }
    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
    return true;
  }

  private static Editor getEditor(@NotNull Project project, VirtualFile file) {
    return FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file), true);
  }

  private boolean executeComposite(@NotNull Project project, ModCompositeCommand cmp) {
    for (ModCommand command : cmp.commands()) {
      boolean status = doExecute(project, command);
      if (!status) {
        return false;
      }
    }
    return true;
  }

  private static boolean executeUpdate(@NotNull Project project, @NotNull ModUpdateFileText upd) {
    VirtualFile file = upd.file();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return false;
    String oldText = upd.oldText();
    String newText = upd.newText();
    if (!document.getText().equals(oldText)) return false;
    List<@NotNull Fragment> ranges = calculateRanges(upd);
    return WriteAction.compute(() -> {
      for (Fragment range : ranges) {
        document.replaceString(range.offset(), range.offset() + range.oldLength(),
                               newText.substring(range.offset(), range.offset() + range.newLength()));
      }
      PsiDocumentManager.getInstance(project).commitDocument(document);
      return true;
    });
  }
  
  private static @NotNull List<@NotNull Fragment> calculateRanges(@NotNull ModUpdateFileText upd) {
    List<@NotNull Fragment> ranges = upd.updatedRanges();
    if (!ranges.isEmpty()) return ranges;
    String oldText = upd.oldText();
    String newText = upd.newText();
    List<DiffFragment> fragments = ComparisonManager.getInstance().compareChars(oldText, newText, ComparisonPolicy.DEFAULT,
                                                                                DumbProgressIndicator.INSTANCE);
    return ContainerUtil.map(fragments, fr -> new Fragment(fr.getStartOffset2(), fr.getEndOffset1() - fr.getStartOffset1(),
                                                           fr.getEndOffset2() - fr.getStartOffset2()));
  }
}
