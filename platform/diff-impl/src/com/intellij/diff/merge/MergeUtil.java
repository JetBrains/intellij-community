/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.merge;

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
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Locale;

import static com.intellij.openapi.ui.Messages.*;

public class MergeUtil {
  @NotNull
  public static Action createSimpleResolveAction(@NotNull MergeResult result,
                                                 @NotNull MergeRequest request,
                                                 @NotNull MergeContext context,
                                                 @NotNull MergeViewer viewer) {
    String caption = getResolveActionTitle(result, request, context);
    return new AbstractAction(caption) {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (result == MergeResult.CANCEL && !showExitWithoutApplyingChangesDialog(viewer, request, context)) {
          return;
        }
        context.finishMerge(result);
      }
    };
  }

  @NotNull
  public static String getResolveActionTitle(@NotNull MergeResult result, @NotNull MergeRequest request, @NotNull MergeContext context) {
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
    return ContainerUtil.list(left, base, right);
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
                                                             @NotNull MergeContext context) {
    Condition<MergeViewer> customHandler = DiffUtil.getUserData(request, context, DiffUserDataKeysEx.MERGE_CANCEL_HANDLER);
    if (customHandler != null) {
      return customHandler.value(viewer);
    }

    Couple<String> customMessage = DiffUtil.getUserData(request, context, DiffUserDataKeysEx.MERGE_CANCEL_MESSAGE);
    if (customMessage != null) {
      String action = customMessage.first;
      String message = customMessage.second;
      return confirmDiscardChanges(viewer.getComponent(), action, message);
    }

    return confirmDiscardChanges(viewer.getComponent(), "Cancel Merge");
  }

  public static boolean confirmDiscardChanges(@NotNull JComponent component,
                                              @NotNull String actionName) {
    String message = "There are unsaved changes in the result file. Discard changes and " + actionName.toLowerCase(Locale.ENGLISH) + " anyway?";
    String action = "Discard Changes and " + actionName;
    return confirmDiscardChanges(component, action, message);
  }

  public static boolean confirmDiscardChanges(@NotNull JComponent component, @NotNull String action, @NotNull String message) {
    String title = action.equals("Cancel Merge") ? "Cancel File Merge" : action;
    String[] options = {action, "Continue Merge"};
    return showConfirmDiscardChangesDialog(component, options, title, message) == YES;
  }

  public static int showConfirmDiscardChangesDialog(@NotNull JComponent component, @NotNull String[] options, String title, String message) {
    if (canShowMacSheetPanel()) {
      return MessagesService.getInstance().showMessageDialog(null, component, "", message, options, options.length-1, 0, getQuestionIcon(), null, false);
    }

    return MessagesService.getInstance().showMessageDialog(null, component, message, title, options, options.length-1, 0, getQuestionIcon(), null, false);
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
}
