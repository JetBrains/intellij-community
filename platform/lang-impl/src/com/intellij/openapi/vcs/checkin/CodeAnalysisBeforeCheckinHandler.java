/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.checkin;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.CodeSmellDetector;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The check-in handler which performs code analysis before check-in. Source code for this class
 * is provided as a sample of using the {@link CheckinHandler} API.
 *
 * @author lesya
 * @since 5.1
 */
public class CodeAnalysisBeforeCheckinHandler extends CheckinHandler {

  private final Project myProject;
  private final CheckinProjectPanel myCheckinPanel;
  private static final Logger LOG = Logger.getInstance("#" + CodeAnalysisBeforeCheckinHandler.class.getName());

  public CodeAnalysisBeforeCheckinHandler(final Project project, CheckinProjectPanel panel) {
    myProject = project;
    myCheckinPanel = panel;
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    final JCheckBox checkBox = new JCheckBox(VcsBundle.message("before.checkin.standard.options.check.smells"));
    return new RefreshableOnComponent() {
      @Override
      public JComponent getComponent() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(checkBox);
        refreshEnable(checkBox);
        return panel;
      }

      @Override
      public void refresh() {
      }

      @Override
      public void saveState() {
        getSettings().CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT = checkBox.isSelected();
      }

      @Override
      public void restoreState() {
        checkBox.setSelected(getSettings().CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT);
      }
    };
  }

  private void refreshEnable(JCheckBox checkBox) {
    if (DumbService.getInstance(myProject).isDumb()) {
      checkBox.setEnabled(false);
      checkBox.setToolTipText("Code analysis is impossible until indices are up-to-date");
    } else {
      checkBox.setEnabled(true);
      checkBox.setToolTipText("");
    }
  }

  private VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  private ReturnResult processFoundCodeSmells(final List<CodeSmellInfo> codeSmells, @Nullable CommitExecutor executor) {
    int errorCount = collectErrors(codeSmells);
    int warningCount = codeSmells.size() - errorCount;
    String commitButtonText = executor != null ? executor.getActionText() : myCheckinPanel.getCommitActionName();
    if (commitButtonText.endsWith("...")) {
      commitButtonText = commitButtonText.substring(0, commitButtonText.length()-3);
    }

    final int answer = Messages.showYesNoCancelDialog(myProject,
      VcsBundle.message("before.commit.files.contain.code.smells.edit.them.confirm.text", errorCount, warningCount),
      VcsBundle.message("code.smells.error.messages.tab.name"), VcsBundle.message("code.smells.review.button"),
      commitButtonText, CommonBundle.getCancelButtonText(), UIUtil.getWarningIcon());
    if (answer == Messages.YES) {
      CodeSmellDetector.getInstance(myProject).showCodeSmellErrors(codeSmells);
      return ReturnResult.CLOSE_WINDOW;
    }
    else if (answer == Messages.CANCEL) {
      return ReturnResult.CANCEL;
    }
    else {
      return ReturnResult.COMMIT;
    }
  }

  private static int collectErrors(final List<CodeSmellInfo> codeSmells) {
    int result = 0;
    for (CodeSmellInfo codeSmellInfo : codeSmells) {
      if (codeSmellInfo.getSeverity() == HighlightSeverity.ERROR) result++;
    }
    return result;
  }

  @Override
  public ReturnResult beforeCheckin(CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
    if (getSettings().CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT) {
      if (DumbService.getInstance(myProject).isDumb()) {
        if (Messages.showOkCancelDialog(myProject,
                                "Code analysis can't be performed while IntelliJ IDEA updates the indices in background.\n" +
                                "You can commit the changes without running inspections, or you can wait until indices are built.",
                                "Code analysis is not possible right now",
                                "&Wait", "&Commit", null) == Messages.OK) {
          return ReturnResult.CANCEL;
        }
        return ReturnResult.COMMIT;
      }

      try {
        final List<CodeSmellInfo> codeSmells =
          CodeSmellDetector.getInstance(myProject).findCodeSmells(new ArrayList<VirtualFile>(myCheckinPanel.getVirtualFiles()));
        if (!codeSmells.isEmpty()) {
          return processFoundCodeSmells(codeSmells, executor);
        }
        else {
          return ReturnResult.COMMIT;
        }
      }
      catch (ProcessCanceledException e) {
        return ReturnResult.CANCEL;
      } catch (Exception e) {
        LOG.error(e);
        if (Messages.showOkCancelDialog(myProject,
                                "Code analysis failed with exception: " + e.getClass().getName() + ": " + e.getMessage(),
                                "Code analysis failed", "&Commit", "&Cancel", null) == Messages.OK) {
          return ReturnResult.COMMIT;
        }
        return ReturnResult.CANCEL;
      }
    }
    else {
      return ReturnResult.COMMIT;
    }
  }
}
