/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.dvcs.push.checkin.examples;

import com.intellij.dvcs.push.checkin.CheckinPushHandler;
import com.intellij.ide.todo.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

//register it as an EP in META-INF/dvcs.xml
//todo: delete before merge into master
public class ExamplePushHandlerAPICheck implements CheckinPushHandler {

  private final Project myProject;

  private int nextAction = 0;

  public ExamplePushHandlerAPICheck(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  @CalledInAny
  public HandlerResult beforePushCheckin(@NotNull List<Change> selectedChanges, @NotNull ProgressIndicator indicator) {
    indicator.setText("Searching for IntelliJ API Changes...");
    nextAction++;

    if (nextAction % 3 == 0) {
      throw new IllegalStateException("Some exception");
    }

    if (nextAction % 3 == 1) {
      //Show 'No internet' message

      Ref<Integer> answer = Ref.create();
      ApplicationManager.getApplication().invokeAndWait(
        () -> {
          int option =
            Messages.showOkCancelDialog(myProject, "No internet", "Failed to Check Usages", "&Wait for internet", "&Push anyway", null);
          answer.set(option);
        }
      , indicator.getModalityState());

      if (answer.get() == Messages.OK) {
        return HandlerResult.ABORT;
      }
      return HandlerResult.OK;
    }

    ExamplePushHandlerEmail.idleUpdateProgress(5000, indicator);

    AtomicReference<Integer> answer = new AtomicReference<>(0);

    ApplicationManager.getApplication().invokeAndWait(() -> {
      String[] buttons = {"&Review", "&Push anyway", "&Cancel"};
      int option = Messages
        .showDialog(myProject, "Your commit may break API used in 100 external plugins",
                    "API Compatibility Problems",
                    null,
                    buttons,
                    0,
                    1,
                    UIUtil.getWarningIcon());
      answer.set(option);
    });

    if (answer.get() == 0) {
      showSomePanel();
      return HandlerResult.ABORT_AND_CLOSE;
    }
    if (answer.get() == 1) {
      return HandlerResult.OK;
    }
    return HandlerResult.ABORT;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "IntelliJ API Changes";
  }

  private void showSomePanel() {
    String title = "For commit (" + DateFormatUtil.formatDateTime(System.currentTimeMillis()) + ")";
    ServiceManager.getService(myProject, TodoView.class).addCustomTodoView(new TodoTreeBuilderFactory() {
      @Override
      public TodoTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project) {
        return new CustomChangelistTodosTreeBuilder(tree, treeModel, myProject, title, Collections.emptyList());
      }
    }, title, new TodoPanelSettings(new TodoPanelSettings()));

    ApplicationManager.getApplication().invokeLater(() -> {
      ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
      if (manager != null) {
        ToolWindow window = manager.getToolWindow("TODO");
        if (window != null) {
          window.show(() -> {
            ContentManager cm = window.getContentManager();
            Content[] contents = cm.getContents();
            if (contents.length > 0) {
              cm.setSelectedContent(contents[contents.length - 1], true);
            }
          });
        }
      }
    }, ModalityState.NON_MODAL, myProject.getDisposed());
  }
}
