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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StopAction extends DumbAwareAction implements AnAction.TransparentUpdate {
  @Override
  public void update(final AnActionEvent e) {
    boolean enable = false;
    Icon icon = getTemplatePresentation().getIcon();
    String description = getTemplatePresentation().getDescription();
    final Presentation presentation = e.getPresentation();

    if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      enable = !getCancellableProcesses(e.getProject()).isEmpty() || !getActiveDescriptors(e.getDataContext()).isEmpty();
    }
    else {
      final ProcessHandler processHandler = getHandler(e.getDataContext());
      if (processHandler != null && !processHandler.isProcessTerminated()) {
        if (!processHandler.isProcessTerminating()) {
          enable = true;
        }
        else if (processHandler instanceof KillableProcess && ((KillableProcess)processHandler).canKillProcess()) {
          enable = true;
          icon = AllIcons.Debugger.KillProcess;
          description = "Kill process";
        }
      }
    }

    presentation.setEnabled(enable);
    presentation.setIcon(icon);
    presentation.setDescription(description);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    ProcessHandler activeProcessHandler = getHandler(dataContext);

    List<Pair<TaskInfo, ProgressIndicator>> backgroundTasks = getCancellableProcesses(e.getProject());
    if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      if (activeProcessHandler != null && !activeProcessHandler.isProcessTerminating() && !activeProcessHandler.isProcessTerminated()
          && backgroundTasks.isEmpty()) {
        stopProcess(activeProcessHandler);
        return;
      }

      Pair<List<HandlerItem>, HandlerItem>
        handlerItems = getItemsList(backgroundTasks, getActiveDescriptors(dataContext), activeProcessHandler);
      if (handlerItems.first.isEmpty()) return;

      final JBList list = new JBList(handlerItems.first);
      if (handlerItems.second != null) list.setSelectedValue(handlerItems.second, true);

      list.setCellRenderer(new GroupedItemsListRenderer(new ListItemDescriptor() {
        @Nullable
        @Override
        public String getTextFor(Object value) {
          return value instanceof HandlerItem ? ((HandlerItem)value).displayName : null;
        }

        @Nullable
        @Override
        public String getTooltipFor(Object value) {
          return null;
        }

        @Nullable
        @Override
        public Icon getIconFor(Object value) {
          return value instanceof HandlerItem ? ((HandlerItem)value).icon : null;
        }

        @Override
        public boolean hasSeparatorAboveOf(Object value) {
          return value instanceof HandlerItem && ((HandlerItem)value).hasSeparator;
        }

        @Nullable
        @Override
        public String getCaptionAboveOf(Object value) {
          return null;
        }
      }));

      final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
      final JBPopup popup = builder
        .setMovable(true)
        .setTitle(handlerItems.first.size() == 1 ? "Confirm process stop" : "Stop process")
        .setFilteringEnabled(new Function<Object, String>() {
          @Override
          public String fun(Object o) {
            return ((HandlerItem)o).displayName;
          }
        })
        .setItemChoosenCallback(new Runnable() {
          @Override
          public void run() {
            HandlerItem item = (HandlerItem)list.getSelectedValue();
            if (item != null) item.stop();
          }
        }).setRequestFocus(true).createPopup();

      popup.showCenteredInCurrentWindow(e.getProject());
    }
    else {
      if (activeProcessHandler != null) {
        stopProcess(activeProcessHandler);
      }
    }
  }

  private static List<Pair<TaskInfo, ProgressIndicator>> getCancellableProcesses(Project project) {
    IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).findFrameFor(project);
    StatusBarEx statusBar = frame == null ? null : (StatusBarEx)frame.getStatusBar();
    if (statusBar == null) return Collections.emptyList();

    return ContainerUtil.findAll(statusBar.getBackgroundProcesses(),
                                 new Condition<Pair<TaskInfo, ProgressIndicator>>() {
                                   @Override
                                   public boolean value(Pair<TaskInfo, ProgressIndicator> pair) {
                                     return pair.first.isCancellable() && !pair.second.isCanceled();
                                   }
                                 });
  }

  private static Pair<List<HandlerItem>, HandlerItem> getItemsList(List<Pair<TaskInfo, ProgressIndicator>> tasks,
                                                                   List<RunContentDescriptor> descriptors,
                                                                   ProcessHandler activeProcessHandler) {
    if (tasks.isEmpty() && descriptors.isEmpty()) return Pair.create(Collections.<HandlerItem>emptyList(), null);

    ArrayList<HandlerItem> items = new ArrayList<HandlerItem>(tasks.size() + descriptors.size());
    HandlerItem selected = null;
    for (RunContentDescriptor descriptor : descriptors) {
      final ProcessHandler handler = descriptor.getProcessHandler();
      if (handler != null) {
        HandlerItem item = new HandlerItem(descriptor.getDisplayName(), descriptor.getIcon(), false) {
          @Override
          void stop() {
            stopProcess(handler);
          }
        };
        items.add(item);
        if (handler == activeProcessHandler) selected = item;
      }
    }

    boolean hasSeparator = true;
    for (final Pair<TaskInfo, ProgressIndicator> eachPair : tasks) {
      items.add(new HandlerItem(eachPair.first.getTitle(), AllIcons.Process.Step_passive, hasSeparator) {
        @Override
        void stop() {
          eachPair.second.cancel();
        }
      });
      hasSeparator = false;
    }
    return Pair.<List<HandlerItem>, HandlerItem>create(items, selected);
  }

  private static void stopProcess(ProcessHandler processHandler) {
    if (processHandler instanceof KillableProcess && processHandler.isProcessTerminating()) {
      ((KillableProcess)processHandler).killProcess();
      return;
    }

    if (processHandler.detachIsDefault()) {
      processHandler.detachProcess();
    }
    else {
      processHandler.destroyProcess();
    }
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
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      final RunContentDescriptor selectedContent =
        project == null ? null : ExecutionManager.getInstance(project).getContentManager().getSelectedContent();
      processHandler = selectedContent == null ? null : selectedContent.getProcessHandler();
    }
    return processHandler;
  }

  @NotNull
  private static List<RunContentDescriptor> getActiveDescriptors(final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
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
      if (processHandler != null && !processHandler.isProcessTerminating() && !processHandler.isProcessTerminated()) {
        activeDescriptors.add(descriptor);
      }
    }
    return activeDescriptors;
  }

  private abstract static class HandlerItem {
    final String displayName;
    final Icon icon;
    final boolean hasSeparator;

    private HandlerItem(String displayName, Icon icon, boolean hasSeparator) {
      this.displayName = displayName;
      this.icon = icon;
      this.hasSeparator =  hasSeparator;
    }

    public String toString() {
      return displayName;
    }

    abstract void stop();
  }
}
