/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.actions;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBList;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StopAction extends DumbAwareAction implements AnAction.TransparentUpdate {
  public static final Icon KILL_PROCESS_ICON = IconLoader.getIcon("/debugger/killProcess.png");

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    ProcessHandler processHandler = getHandler(dataContext);
    if ((processHandler == null || processHandler.isProcessTerminated()) && ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      final Collection<HandlerItem> handlerItems = getItemsList(getActiveDescriptors(dataContext));
      if (!handlerItems.isEmpty()) {
        final JBList list = new JBList(handlerItems);
        list.installCellRenderer(new NotNullFunction<HandlerItem, JComponent>() {
          final JLabel label = new JLabel();
          @NotNull
          public JComponent fun(HandlerItem item) {
            label.setIcon(item.icon);
            label.setIconTextGap(6);
            label.setText(item.displayName);
            return label;
          }
        });
        final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
        final String title = handlerItems.size() == 1? "Confirm process stop" : "Select process to stop";

        final JBPopup popup = builder.setTitle(title).setItemChoosenCallback(new Runnable() {
          public void run() {
            final HandlerItem item = (HandlerItem)list.getSelectedValue();
            if (item != null) {
              performAction(item.handler);
            }
          }
        }).setRequestFocus(true).createPopup();

        popup.showInBestPositionFor(dataContext);
      }
    }

    if(processHandler != null) {
      performAction(processHandler);
    }
  }

  private static Collection<HandlerItem> getItemsList(List<RunContentDescriptor> descriptors) {
    if (descriptors.isEmpty()) {
      return Collections.emptyList();
    }
    final ArrayList<HandlerItem> items = new ArrayList<HandlerItem>();
    for (RunContentDescriptor descriptor : descriptors) {
      final ProcessHandler handler = descriptor.getProcessHandler();
      if (handler != null) {
        items.add(new HandlerItem(handler, descriptor.getDisplayName(), descriptor.getIcon()));
      }
    }
    return items;
  }

  private static void performAction(ProcessHandler processHandler) {
    if (processHandler instanceof KillableProcess && processHandler.isProcessTerminating()) {
      ((KillableProcess)processHandler).killProcess();
      return;
    }

    if(processHandler.detachIsDefault()) {
      processHandler.detachProcess();
    }
    else {
      processHandler.destroyProcess();
    }
  }

  public void update(final AnActionEvent e) {
    boolean enable = false;
    Icon icon = getTemplatePresentation().getIcon();
    String description = getTemplatePresentation().getDescription();
    final Presentation presentation = e.getPresentation();

    if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      final List<RunContentDescriptor> descriptors = getActiveDescriptors(e.getDataContext());
      for (RunContentDescriptor descriptor : descriptors) {
        final ProcessHandler handler = descriptor.getProcessHandler();
        if (handler != null && !handler.isProcessTerminated()) {
          enable = true;
          break;
        }
      }
    }
    else {
      final ProcessHandler processHandler = getHandler(e.getDataContext());
      if (processHandler != null && !processHandler.isProcessTerminated()) {
        if (!processHandler.isProcessTerminating()) {
          enable = true;
        }
        else if (processHandler instanceof KillableProcess && ((KillableProcess)processHandler).canKillProcess()) {
          enable = true;
          icon = KILL_PROCESS_ICON;
          description = "Kill process";
        }
      }
    }

    presentation.setEnabled(enable);
    presentation.setIcon(icon);
    presentation.setDescription(description);
  }

  @Nullable
  private static ProcessHandler getHandler(final DataContext dataContext) {
    final RunContentDescriptor contentDescriptor = RunContentManager.RUN_CONTENT_DESCRIPTOR.getData(dataContext);
    final ProcessHandler processHandler;
    if (contentDescriptor != null) {
      // toolwindow case
      processHandler = contentDescriptor.getProcessHandler();
    }
    else {
      // main menu toolbar
      final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      final RunContentDescriptor selectedContent = project == null? null : ExecutionManager.getInstance(project).getContentManager().getSelectedContent();
      processHandler = selectedContent == null? null : selectedContent.getProcessHandler();
    }
    return processHandler;
  }

  @NotNull
  private static List<RunContentDescriptor> getActiveDescriptors(final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return Collections.emptyList();
    }
    final List<RunContentDescriptor> runningProcesses = ExecutionManager.getInstance(project).getContentManager().getAllDescriptors();
    if (runningProcesses.isEmpty()) {
      return Collections.emptyList();
    }
    final List<RunContentDescriptor> activeDescriptors = new ArrayList<RunContentDescriptor>();
    for (RunContentDescriptor descriptor : runningProcesses) {
      final ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler != null && !processHandler.isProcessTerminated()) {
        activeDescriptors.add(descriptor);
      }
    }
    return activeDescriptors;
  }

  private static class HandlerItem {
    private final ProcessHandler handler;
    private final String displayName;
    private final Icon icon;

    private HandlerItem(final ProcessHandler processHandler, final String displayName1, Icon icon) {
      this.handler = processHandler;
      displayName = displayName1;
      this.icon = icon;
    }

    public String toString() {
      return displayName;
    }
  }

}
