// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.merge;

import com.intellij.CommonBundle;
import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.diff.DiffContext;
import com.intellij.diff.merge.MergeTool.MergeViewer;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.intellij.openapi.project.ProjectUtil.isProjectOrWorkspaceFile;

public final class MergeUtil {
  @NotNull
  public static Action createSimpleResolveAction(@NotNull MergeResult result,
                                                 @NotNull MergeRequest request,
                                                 @NotNull MergeContext context,
                                                 @NotNull MergeViewer viewer,
                                                 boolean contentWasModified) {
    String caption = getResolveActionTitle(result, request, context);
    return new AbstractAction(caption) {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (result == MergeResult.CANCEL && !showExitWithoutApplyingChangesDialog(viewer, request, context, contentWasModified)) {
          return;
        }
        context.finishMerge(result);
      }
    };
  }

  @Nls
  @NotNull
  public static String getResolveActionTitle(@NotNull MergeResult result, @Nullable MergeRequest request, @Nullable MergeContext context) {
    Function<MergeResult, String> getter = DiffUtil.getUserData(request, context, DiffUserDataKeysEx.MERGE_ACTION_CAPTIONS);
    String message = getter != null ? getter.fun(result) : null;
    if (message != null) return message;

    switch (result) {
      case CANCEL:
        return DiffBundle.message("button.merge.resolve.cancel");
      case LEFT:
        return DiffBundle.message("button.merge.resolve.accept.left");
      case RIGHT:
        return DiffBundle.message("button.merge.resolve.accept.right");
      case RESOLVED:
        return DiffBundle.message("button.merge.resolve.apply");
      default:
        throw new IllegalArgumentException(result.toString());
    }
  }

  @NotNull
  public static List<String> notNullizeContentTitles(@NotNull List<String> mergeContentTitles) {
    String left = StringUtil.notNullize(ThreeSide.LEFT.select(mergeContentTitles), DiffBundle.message("merge.version.title.our"));
    String base = StringUtil.notNullize(ThreeSide.BASE.select(mergeContentTitles), DiffBundle.message("merge.version.title.base"));
    String right = StringUtil.notNullize(ThreeSide.RIGHT.select(mergeContentTitles), DiffBundle.message("merge.version.title.their"));
    return Arrays.asList(left, base, right);
  }

  public static class ProxyDiffContext extends DiffContext {
    @NotNull private final MergeContext myMergeContext;

    public ProxyDiffContext(@NotNull MergeContext mergeContext) {
      myMergeContext = mergeContext;
    }

    @Nullable
    @Override
    public Project getProject() {
      return myMergeContext.getProject();
    }

    @Override
    public boolean isWindowFocused() {
      return true;
    }

    @Override
    public boolean isFocusedInWindow() {
      return myMergeContext.isFocusedInWindow();
    }

    @Override
    public void requestFocusInWindow() {
      myMergeContext.requestFocusInWindow();
    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return myMergeContext.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      myMergeContext.putUserData(key, value);
    }
  }

  public static boolean showExitWithoutApplyingChangesDialog(@NotNull MergeViewer viewer,
                                                             @NotNull MergeRequest request,
                                                             @NotNull MergeContext context,
                                                             boolean contentWasModified) {
    Condition<MergeViewer> customHandler = DiffUtil.getUserData(request, context, DiffUserDataKeysEx.MERGE_CANCEL_HANDLER);
    if (customHandler != null) {
      return customHandler.value(viewer);
    }

    return !contentWasModified || showExitWithoutApplyingChangesDialog(viewer.getComponent(), request, context);
  }

  public static boolean showExitWithoutApplyingChangesDialog(@NotNull JComponent component,
                                                             @NotNull MergeRequest request,
                                                             @NotNull MergeContext context) {
    Couple<String> customMessage = DiffUtil.getUserData(request, context, DiffUserDataKeysEx.MERGE_CANCEL_MESSAGE);
    if (customMessage != null) {
      String title = customMessage.first;
      String message = customMessage.second;
      return Messages.showConfirmationDialog(component, message, title,
                                             CommonBundle.message("button.without.mnemonic.yes"),
                                             CommonBundle.message("button.without.mnemonic.no")) == Messages.YES;
    }

    return showConfirmDiscardChangesDialog(component, DiffBundle.message("button.cancel.merge"), true);
  }

  public static boolean showConfirmDiscardChangesDialog(@NotNull JComponent parent,
                                                        @NotNull @Nls String actionName,
                                                        boolean contentWasModified) {
    if (!contentWasModified) return true;
    return Messages.showConfirmationDialog(
      parent,
      DiffBundle.message("label.merge.unsaved.changes.discard.and.do.anyway", actionName.toLowerCase(Locale.ENGLISH)),
      actionName,
      DiffBundle.message("button.discard.changes.and.do", actionName),
      DiffBundle.message("button.continue.merge")) == Messages.YES;
  }

  public static boolean shouldRestoreOriginalContentOnCancel(@NotNull MergeRequest request) {
    MergeCallback callback = MergeCallback.getCallback(request);
    if (callback.checkIsValid()) return true;
    return Messages.showYesNoDialog(DiffBundle.message("merge.conflict.is.outdated"),
                                    DiffBundle.message("cancel.visual.merge.dialog.title"),
                                    CommonBundle.message("button.without.mnemonic.restore"),
                                    CommonBundle.message("button.without.mnemonic.do.nothing"),
                                    Messages.getQuestionIcon()) == Messages.YES;
  }

  public static void reportProjectFileChangeIfNeeded(@Nullable Project project, @Nullable VirtualFile file) {
    if (project != null && file != null && isProjectFile(file)) {
      StoreReloadManager.getInstance().saveChangedProjectFile(file, project);
    }
  }

  private static boolean isProjectFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    if (isProjectOrWorkspaceFile(file)) return true;

    ProjectOpenProcessor importProvider = ProjectOpenProcessor.getImportProvider(file);
    return importProvider != null && importProvider.lookForProjectsInDirectory();
  }
}
