// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.modcommand.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.NotNullFunction;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ModCommandServiceImpl implements ModCommandService {
  @Override
  @NotNull
  public IntentionAction wrap(@NotNull ModCommandAction action) {
    return new ModCommandActionWrapper(action);
  }

  @Override
  @Nullable
  public ModCommandAction unwrap(@NotNull IntentionAction action) {
    while (action instanceof IntentionActionDelegate delegate) {
      action = delegate.getDelegate();
    }
    return action instanceof ModCommandActionWrapper wrapper ? wrapper.action() : null;
  }

  @Override
  public @NotNull ModStatus execute(@NotNull Project project, @NotNull ModCommand command) {
    if (command instanceof ModUpdatePsiFile upd) {
      return executeUpdate(project, upd);
    }
    if (command instanceof ModCompositeCommand cmp) {
      return executeComposite(project, cmp);
    }
    if (command instanceof ModNavigate nav) {
      return executeNavigate(project, nav);
    }
    if (command instanceof ModNothing) {
      return ModStatus.SUCCESS;
    }
    if (command instanceof ModChooseTarget<?> cht) {
      return executeChoose(project, cht);
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  @NotNull
  private static <T extends @NotNull PsiElement> ModStatus executeChoose(@NotNull Project project, ModChooseTarget<@NotNull T> cht) {
    String name = CommandProcessor.getInstance().getCurrentCommandName();
    var elements = cht.elements();
    var nextStep = cht.nextStep();
    if (elements.isEmpty()) return ModStatus.ABORT;
    T element = elements.get(0).element();
    if (elements.size() == 1) {
      executeNextStep(project, nextStep, element, name);
      return ModStatus.DEFERRED;
    }
    VirtualFile file = element.getContainingFile().getVirtualFile();
    if (file == null) return ModStatus.ABORT;
    Editor editor = getEditor(project, file);
    if (editor == null) return ModStatus.ABORT;
    var map = StreamEx.of(elements).toMap(ModChooseTarget.ListItem::element, Function.identity());
    List<T> elementList = ContainerUtil.map(elements, ModChooseTarget.ListItem::element);
    Pass<T> callback = new Pass<>() {
      @Override
      public void pass(T t) {
        executeNextStep(project, cht.nextStep(), t, name);
      }
    };
    //noinspection SuspiciousMethodCalls
    NotNullFunction<PsiElement, TextRange> ranger = e -> map.get(e).selection();
    IntroduceTargetChooser.showChooser(editor, elementList, callback,
                                       e -> map.get(e).toString(), cht.title(), cht.selection(),
                                       ranger);
    return ModStatus.DEFERRED;
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

  @NotNull
  private static ModStatus executeNavigate(@NotNull Project project, ModNavigate nav) {
    VirtualFile file = nav.file();
    int selectionStart = nav.selectionStart();
    int selectionEnd = nav.selectionEnd();
    int caret = nav.caret();

    Editor editor = getEditor(project, file);
    if (editor == null) return ModStatus.ABORT;
    if (selectionStart != -1 && selectionEnd != -1) {
      editor.getSelectionModel().setSelection(selectionStart, selectionEnd);
    }
    if (caret != -1) {
      editor.getCaretModel().moveToOffset(caret);
    }
    return ModStatus.SUCCESS;
  }

  private static Editor getEditor(@NotNull Project project, VirtualFile file) {
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file);
    if (fileEditor instanceof TextEditor textEditor) {
      return textEditor.getEditor();
    }
    return null;
  }

  @NotNull
  private ModStatus executeComposite(@NotNull Project project, ModCompositeCommand cmp) {
    for (ModCommand command : cmp.commands()) {
      ModStatus status = execute(project, command);
      if (status != ModStatus.SUCCESS) {
        return status;
      }
    }
    return ModStatus.SUCCESS;
  }

  private static @NotNull ModStatus executeUpdate(@NotNull Project project, @NotNull ModUpdatePsiFile upd) {
    PsiFile file = upd.file();
    String oldText = upd.oldText();
    String newText = upd.newText();
    if (!file.textMatches(oldText)) return ModStatus.ABORT;
    List<LineFragment> fragments = ComparisonManager.getInstance().compareLines(oldText, newText, ComparisonPolicy.DEFAULT,
                                                                                DumbProgressIndicator.INSTANCE);
    Document document = file.getViewProvider().getDocument();
    return WriteAction.compute(() -> {
      StreamEx.ofReversed(fragments)
          .forEach(fragment -> {
            document.replaceString(fragment.getStartOffset1(), fragment.getEndOffset1(), 
                                   newText.substring(fragment.getStartOffset2(), fragment.getEndOffset2()));
          });
      PsiDocumentManager.getInstance(project).commitDocument(document);
      return ModStatus.SUCCESS;
    });
  }
}
