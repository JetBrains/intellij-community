// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.merge;

import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.merge.MergeTool.MergeViewer;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.intellij.openapi.project.ProjectUtil.isProjectOrWorkspaceFile;

public class MergeUtil {
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

  @NotNull
  public static String getResolveActionTitle(@NotNull MergeResult result, @Nullable MergeRequest request, @Nullable MergeContext context) {
    Function<MergeResult, String> getter = DiffUtil.getUserData(request, context, DiffUserDataKeysEx.MERGE_ACTION_CAPTIONS);
    String message = getter != null ? getter.fun(result) : null;
    if (message != null) return message;

    switch (result) {
      case CANCEL:
        return "Cancel";
      case LEFT:
        return "Accept Left";
      case RIGHT:
        return "Accept Right";
      case RESOLVED:
        return "Apply";
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
      return Messages.showConfirmationDialog(component, message, title, "Yes", "No") == Messages.YES;
    }

    return showConfirmDiscardChangesDialog(component, "Cancel Merge", true);
  }

  public static boolean showConfirmDiscardChangesDialog(@NotNull JComponent parent,
                                                        @NotNull String actionName,
                                                        boolean contentWasModified) {
    if (!contentWasModified) return true;
    String message = "There are unsaved changes in the result file. Discard changes and " + actionName.toLowerCase(Locale.ENGLISH) + " anyway?";
    String yesText = "Discard Changes and " + actionName;
    String noText = "Continue Merge";

    return Messages.showConfirmationDialog(parent, message, actionName, yesText, noText) == Messages.YES;
  }

  public static boolean shouldRestoreOriginalContentOnCancel(@NotNull MergeRequest request) {
    MergeCallback callback = MergeCallback.getCallback(request);
    if (callback.checkIsValid()) return true;
    return Messages.showYesNoDialog("Merge conflict is outdated. Restore file content prior to conflict resolve start?",
                                    DiffBundle.message("cancel.visual.merge.dialog.title"), "Restore", "Do nothing",
                                    Messages.getQuestionIcon()) == Messages.YES;
  }

  public static void putRevisionInfos(@NotNull MergeRequest request, @NotNull MergeData data) {
    if (request instanceof ThreesideMergeRequest) {
      List<? extends DiffContent> contents = ((ThreesideMergeRequest)request).getContents();
      putRevisionInfo(contents, data);
    }
  }

  public static void putRevisionInfos(@NotNull DiffRequest request, @NotNull MergeData data) {
    if (request instanceof ContentDiffRequest) {
      List<? extends DiffContent> contents = ((ContentDiffRequest)request).getContents();
      if (contents.size() == 3) {
        putRevisionInfo(contents, data);
      }
    }
  }

  private static void putRevisionInfo(@NotNull List<? extends DiffContent> contents, @NotNull MergeData data) {
    for (ThreeSide side : ThreeSide.values()) {
      DiffContent content = side.select(contents);
      FilePath filePath = side.select(data.CURRENT_FILE_PATH, data.ORIGINAL_FILE_PATH, data.LAST_FILE_PATH);
      VcsRevisionNumber revision = side.select(data.CURRENT_REVISION_NUMBER, data.ORIGINAL_REVISION_NUMBER, data.LAST_REVISION_NUMBER);
      if (filePath != null && revision != null) {
        content.putUserData(DiffUserDataKeysEx.REVISION_INFO, Pair.create(filePath, revision));
      }
    }
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
