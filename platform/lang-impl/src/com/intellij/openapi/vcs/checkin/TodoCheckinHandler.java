/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.processing.DiffCorrection;
import com.intellij.openapi.diff.impl.processing.DiffFragmentsProcessor;
import com.intellij.openapi.diff.impl.processing.DiffPolicy;
import com.intellij.openapi.diff.impl.processing.TextCompareProcessor;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.util.PairConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import sun.util.LocaleServiceProviderPool;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author irengrig
 *         Date: 2/17/11
 *         Time: 5:54 PM
 */
public class TodoCheckinHandler extends CheckinHandler {
  private final Project myProject;
  private final CheckinProjectPanel myCheckinProjectPanel;
  private VcsConfiguration myConfiguration;
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.checkin.TodoCheckinHandler");

  public TodoCheckinHandler(CheckinProjectPanel checkinProjectPanel) {
    myProject = checkinProjectPanel.getProject();
    myCheckinProjectPanel = checkinProjectPanel;
    myConfiguration = VcsConfiguration.getInstance(myProject);
  }

  @Override
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    final JCheckBox checkBox = new JCheckBox(VcsBundle.message("before.checkin.new.todo.check"));
    return new RefreshableOnComponent() {
      public JComponent getComponent() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(checkBox);
        refreshEnable(checkBox);
        return panel;
      }

      public void refresh() {
      }

      public void saveState() {
        myConfiguration.CHECK_NEW_TODO = checkBox.isSelected();
      }

      public void restoreState() {
        checkBox.setSelected(myConfiguration.CHECK_NEW_TODO);
      }
    };
  }

  private void refreshEnable(JCheckBox checkBox) {
    if (DumbService.getInstance(myProject).isDumb()) {
      checkBox.setEnabled(false);
      checkBox.setToolTipText("TODO check is impossible until indices are up-to-date");
    } else {
      checkBox.setEnabled(true);
      checkBox.setToolTipText("");
    }
  }

  @Override
  public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
    if (! myConfiguration.CHECK_NEW_TODO) return ReturnResult.COMMIT;
    if (DumbService.getInstance(myProject).isDumb()) {
      final String todoName = VcsBundle.message("before.checkin.new.todo.check");
      if (Messages.showDialog(myProject,
                              todoName +
                              " can't be performed while IntelliJ IDEA updates the indices in background.\n" +
                              "You can commit the changes without running checks, or you can wait until indices are built.",
                              todoName + " is not possible right now",
                              new String[]{"&Commit", "&Wait"}, 1, null) != 0) {
        return ReturnResult.CANCEL;
      }
      return ReturnResult.COMMIT;
    }
    final Collection<Change> changes = myCheckinProjectPanel.getSelectedChanges();
    final TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, changes);

    // todo: progress, read actions, exceptions handling (report !), report "bad" files
    // todo: special pattern
    // todo: report window
    final Runnable runnable = new Runnable() {
      public void run() {
        worker.execute();
        }
    };
    final boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, "", true, myProject);
    if (! completed || (worker.getAddedOrEditedTodos().isEmpty() && worker.getInChangedTodos().isEmpty() &&
      worker.getSkipped().isEmpty())) return ReturnResult.COMMIT;

    return showResults(worker, executor);
  }

  private ReturnResult showResults(TodoCheckinHandlerWorker worker, CommitExecutor executor) {
    String commitButtonText = executor != null ? executor.getActionText() : myCheckinProjectPanel.getCommitActionName();
    if (commitButtonText.endsWith("...")) {
      commitButtonText = commitButtonText.substring(0, commitButtonText.length()-3);
    }

    final StringBuilder text = new StringBuilder();
    if (worker.getAddedOrEditedTodos().isEmpty() && worker.getInChangedTodos().isEmpty()) {
      text.append("No new, edited, or located in changed fragments TODO items found.\n").append(worker.getSkipped().size())
        .append(" file(s) were skipped.\nWould you like to review them?");
    } else {
      if (worker.getAddedOrEditedTodos().isEmpty()) {
        text.append("There were ").append(worker.getInChangedTodos().size()).append(" located in changed fragments TODO item(s) found.\n");
      } else if (worker.getInChangedTodos().isEmpty()) {
        text.append("There were ").append(worker.getAddedOrEditedTodos().size()).append(" added or edited TODO item(s) found.\n");
      } else {
        text.append("There were ").append(worker.getAddedOrEditedTodos().size()).append(" added or edited,\nand ")
          .append(worker.getInChangedTodos().size()).append(" located in changed fragments TODO item(s) found.\n");
      }
      if (! worker.getSkipped().isEmpty()) {
        text.append(worker.getSkipped().size()).append(" file(s) were skipped.\n");
      }
      text.append("Would you like to review them?");
    }

    final int answer = Messages.showDialog(text.toString(), "TODO", new String[]{VcsBundle.message("todo.in.new.review.button"),
        commitButtonText, CommonBundle.getCancelButtonText()}, 0, UIUtil.getWarningIcon());
    if (answer == 0) {
      // show for review
      return ReturnResult.CLOSE_WINDOW;
    }
    else if (answer == 2 || answer == -1) {
      return ReturnResult.CANCEL;
    }
    else {
      return ReturnResult.COMMIT;
    }
  }
}
