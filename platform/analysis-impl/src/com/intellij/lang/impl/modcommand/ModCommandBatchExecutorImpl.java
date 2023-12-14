// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.modcommand.*;
import com.intellij.modcommand.ModUpdateFileText.Fragment;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * An {@link ModCommandExecutor} service implementation that supports batch execution only. 
 * Interactive execution request executes like in batch. 
 */
public class ModCommandBatchExecutorImpl implements ModCommandExecutor {
  /**
   * {@inheritDoc}
   * 
   * @implNote this implementation executes the action like in batch, without displaying any UI.
   * 
   * @param context current context
   * @param command a command to execute
   * @param editor ignored by this implementation, can be null
   */
  @Override
  public void executeInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor) {
    BatchExecutionResult result = executeInBatch(context, command);
    if (result == Result.INTERACTIVE || result == Result.CONFLICTS) {
      throw new UnsupportedOperationException(result.getMessage());
    }
    if (result instanceof Error error) {
      throw new RuntimeException(error.getMessage());
    }
  }

  @Override
  public @NotNull BatchExecutionResult executeInBatch(@NotNull ActionContext context, @NotNull ModCommand command) {
    if (!ensureWritable(context.project(), command)) {
      return new Error(AnalysisBundle.message("modcommand.executor.error.files.are.marked.as.readonly"));
    }
    return doExecuteInBatch(context, command);
  }

  private BatchExecutionResult doExecuteInBatch(@NotNull ActionContext context, @NotNull ModCommand command) {
    Project project = context.project();
    if (command.isEmpty()) {
      return Result.NOTHING;
    }
    if (command instanceof ModUpdateFileText upd) {
      return executeUpdate(project, upd) ? Result.SUCCESS : Result.ABORT;
    }
    if (command instanceof ModCreateFile create) {
      String message = executeCreate(project, create);
      return message == null ? Result.SUCCESS : new Error(message);
    }
    if (command instanceof ModDeleteFile deleteFile) {
      String message = executeDelete(deleteFile);
      return message == null ? Result.SUCCESS : new Error(message);
    }
    if (command instanceof ModCompositeCommand cmp) {
      BatchExecutionResult result = Result.NOTHING;
      for (ModCommand subCommand : cmp.commands()) {
        result = result.compose(doExecuteInBatch(context, subCommand));
        if (result == Result.ABORT || result instanceof Error) break;
      }
      return result;
    }
    if (command instanceof ModChooseAction chooser) {
      return executeChooseInBatch(context, chooser);
    }
    if (command instanceof ModChooseMember member) {
      ModCommand nextCommand = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> ReadAction.nonBlocking(() -> member.nextCommand().apply(member.defaultSelection())).executeSynchronously(),
        member.title(), true, context.project());
      executeInBatch(context, nextCommand);
    }
    if (command instanceof ModNavigate || command instanceof ModHighlight ||
        command instanceof ModCopyToClipboard || command instanceof ModStartRename ||
        command instanceof ModStartTemplate || command instanceof ModUpdateSystemOptions) {
      return Result.INTERACTIVE;
    }
    if (command instanceof ModShowConflicts) {
      return Result.CONFLICTS;
    }
    if (command instanceof ModDisplayMessage message) {
      if (message.kind() == ModDisplayMessage.MessageKind.ERROR) {
        return new Error(message.messageText());
      }
      return Result.INTERACTIVE;
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  private BatchExecutionResult executeChooseInBatch(@NotNull ActionContext context, ModChooseAction chooser) {
    ModCommandAction action = StreamEx.of(chooser.actions()).filter(act -> act.getPresentation(context) != null)
      .findFirst().orElse(null);
    if (action == null) return Result.NOTHING;

    String name = chooser.title();
    ModCommand next = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.nonBlocking(() -> {
        if (action.getPresentation(context) == null) return null;
        return action.perform(context);
      }).executeSynchronously(), name, true, context.project());
    if (next == null) return Result.ABORT;
    return executeInBatch(context, next);
  }

  @Nls
  protected String executeDelete(ModDeleteFile file) {
    try {
      WriteAction.run(() -> file.file().delete(this));
      return null;
    }
    catch (IOException e) {
      return e.getLocalizedMessage();
    }
  }

  @Nls
  protected String executeCreate(@NotNull Project project, @NotNull ModCreateFile create) {
    FutureVirtualFile file = create.file();
    VirtualFile parent = actualize(file.getParent());
    try {
      return WriteAction.compute(() -> {
        VirtualFile newFile = parent.createChildData(this, file.getName());
        PsiFile psiFile = PsiManager.getInstance(project).findFile(newFile);
        if (psiFile == null) return AnalysisBundle.message("modcommand.executor.unable.to.find.the.new.file", file.getName());
        Document document = psiFile.getViewProvider().getDocument();
        document.setText(create.text());
        PsiDocumentManager.getInstance(project).commitDocument(document);
        return null;
      });
    }
    catch (IOException e) {
      return e.getLocalizedMessage();
    }
  }

  @Override
  public void executeForFileCopy(@NotNull ModCommand command, @NotNull PsiFile file) {
    for (ModCommand cmd : command.unpack()) {
      if (cmd instanceof ModUpdateFileText updateFileText) {
        if (!updateFileText.file().equals(file.getOriginalFile().getVirtualFile())) {
          throw new UnsupportedOperationException("The command updates non-current file");
        }
        updateText(file.getProject(), file.getViewProvider().getDocument(), updateFileText);
      }
      else if (!(cmd instanceof ModNavigate) && !(cmd instanceof ModHighlight)) {
        throw new UnsupportedOperationException("Unexpected command: " + command);
      }
    }
  }

  protected static boolean ensureWritable(@NotNull Project project, @NotNull ModCommand command) {
    Collection<VirtualFile> files = ContainerUtil.filter(command.modifiedFiles(), f -> !(f instanceof LightVirtualFile));
    return files.isEmpty() || ReadonlyStatusHandler.ensureFilesWritable(project, files.toArray(VirtualFile.EMPTY_ARRAY));
  }

  protected static VirtualFile actualize(@NotNull VirtualFile file) {
    return file instanceof FutureVirtualFile future
           ? actualize(future.getParent()).findChild(future.getName())
           : file;
  }

  private void updateText(@NotNull Project project, @NotNull Document document, @NotNull ModUpdateFileText upd)
    throws IllegalStateException {
    String oldText = upd.oldText();
    if (!document.getText().equals(oldText)) {
      throw new IllegalStateException("Old text doesn't match");
    }
    List<@NotNull Fragment> ranges = calculateRanges(upd);
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    applyRanges(document, ranges, upd.newText());
    manager.commitDocument(document);
  }

  protected boolean executeUpdate(@NotNull Project project, @NotNull ModUpdateFileText upd) {
    VirtualFile file = upd.file();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return false;
    String oldText = upd.oldText();
    if (!document.getText().equals(oldText)) return false;
    List<@NotNull Fragment> ranges = calculateRanges(upd);
    return WriteAction.compute(() -> {
      applyRanges(document, ranges, upd.newText());
      PsiDocumentManager.getInstance(project).commitDocument(document);
      return true;
    });
  }

  private static void applyRanges(@NotNull Document document, List<@NotNull Fragment> ranges, String newText) {
    for (Fragment range : ranges) {
      document.replaceString(range.offset(), range.offset() + range.oldLength(),
                             newText.substring(range.offset(), range.offset() + range.newLength()));
    }
  }

  protected @NotNull List<@NotNull Fragment> calculateRanges(@NotNull ModUpdateFileText upd) {
    return List.of(new Fragment(0, upd.oldText().length(), upd.newText().length()));
  }
}
