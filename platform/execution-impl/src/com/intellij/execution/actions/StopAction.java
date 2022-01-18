// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.IconUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StopAction extends DumbAwareAction {
  private WeakReference<JBPopup> myActivePopupRef = null;

  private static boolean isPlaceGlobal(@NotNull AnActionEvent e) {
    return ActionPlaces.isMainMenuOrActionSearch(e.getPlace())
           || ActionPlaces.MAIN_TOOLBAR.equals(e.getPlace())
           || ActionPlaces.NAVIGATION_BAR_TOOLBAR.equals(e.getPlace())
           || ActionPlaces.TOUCHBAR_GENERAL.equals(e.getPlace());
  }
  @Override
  public void update(@NotNull final AnActionEvent e) {
    boolean enable = false;
    Icon icon = getActionIcon(e);
    String description = getTemplatePresentation().getDescription();
    Presentation presentation = e.getPresentation();
    if (isPlaceGlobal(e)) {
      List<RunContentDescriptor> stoppableDescriptors = getActiveStoppableDescriptors(e.getDataContext());
      int stopCount = stoppableDescriptors.size();
      enable = stopCount >= 1;
      if (stopCount > 1) {
        presentation.setText(getTemplatePresentation().getText() + "...");
        icon = IconUtil.addText(icon, String.valueOf(stopCount));
      }
      else if (stopCount == 1) {
          presentation.setText(ExecutionBundle.messagePointer("stop.configuration.action.name",
                                                       StringUtil.escapeMnemonics(
                                                         StringUtil.notNullize(stoppableDescriptors.get(0).getDisplayName()))));
      }
    }
    else {
      RunContentDescriptor contentDescriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
      ProcessHandler processHandler = contentDescriptor == null ? null : contentDescriptor.getProcessHandler();
      if (processHandler != null && !processHandler.isProcessTerminated()) {
        if (!processHandler.isProcessTerminating()) {
          enable = true;
        }
        else if (processHandler instanceof KillableProcess && ((KillableProcess)processHandler).canKillProcess()) {
          enable = true;
          icon = AllIcons.Debugger.KillProcess;
          description = ExecutionBundle.message("action.terminating.process.progress.kill.description");
        }
      }

      RunProfile runProfile = e.getData(LangDataKeys.RUN_PROFILE);
      if (runProfile == null && contentDescriptor == null) {
        presentation.setText(getTemplatePresentation().getText());
      }
      else {
        presentation.setText(ExecutionBundle.messagePointer("stop.configuration.action.name",
                                                     StringUtil.escapeMnemonics(runProfile == null
                                                                                ? StringUtil.notNullize(contentDescriptor.getDisplayName())
                                                                                : runProfile.getName())));
      }
    }

    presentation.setEnabled(enable);
    presentation.setIcon(icon);
    presentation.setDescription(description);
  }

  protected Icon getActionIcon(@NotNull final AnActionEvent e) {
    return getTemplatePresentation().getIcon();
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = e.getProject();
    List<RunContentDescriptor> stoppableDescriptors = getActiveStoppableDescriptors(dataContext);
    int stopCount = stoppableDescriptors.size();
    if (isPlaceGlobal(e)) {
      if (stopCount == 1) {
        ExecutionManagerImpl.stopProcess(stoppableDescriptors.get(0));
        return;
      }

      Pair<List<HandlerItem>, HandlerItem>
        handlerItems = getItemsList(project, stoppableDescriptors, getRecentlyStartedContentDescriptor(dataContext));
      if (handlerItems == null || handlerItems.first.isEmpty()) {
        return;
      }

      HandlerItem stopAllItem =
        new HandlerItem(ExecutionBundle.message("stop.all", KeymapUtil.getFirstKeyboardShortcutText("Stop")), getActionIcon(e),
                        true) {
          @Override
          void stop() {
            for (HandlerItem item : handlerItems.first) {
              if(item == this) continue;
              item.stop();
            }
          }
        };
      JBPopup activePopup = SoftReference.dereference(myActivePopupRef);
      if (activePopup != null) {
        stopAllItem.stop();
        activePopup.cancel();
        return;
      }

      List<HandlerItem> items = handlerItems.first;
      if (stopCount > 1) {
        items.add(stopAllItem);
      }

      IPopupChooserBuilder<HandlerItem> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(items)
        .setRenderer(new GroupedItemsListRenderer<>(new ListItemDescriptorAdapter<>() {
          @Nullable
          @Override
          public String getTextFor(HandlerItem item) {
            return item.displayName;
          }

          @Nullable
          @Override
          public Icon getIconFor(HandlerItem item) {
            return item.icon;
          }

          @Override
          public boolean hasSeparatorAboveOf(HandlerItem item) {
            return item.hasSeparator;
          }
        }))
        .setMovable(true)
        .setTitle(items.size() == 1 ? ExecutionBundle.message("confirm.process.stop") : ExecutionBundle.message("stop.process"))
        .setNamerForFiltering(o -> o.displayName)
        .setItemsChosenCallback((valuesList) -> {
          for (HandlerItem item : valuesList) {
            item.stop();
          }
        })
        .addListener(new JBPopupListener() {
          @Override
          public void onClosed(@NotNull LightweightWindowEvent event) {
            myActivePopupRef = null;
          }
        })
        .setRequestFocus(true);
      if (handlerItems.second != null) {
        builder.setSelectedValue(handlerItems.second, true);
      }
      JBPopup popup = builder
        .createPopup();

      myActivePopupRef = new WeakReference<>(popup);
      InputEvent inputEvent = e.getInputEvent();
      Component component = inputEvent != null ? inputEvent.getComponent() : null;
      if (component != null && (ActionPlaces.MAIN_TOOLBAR.equals(e.getPlace())
                                || ActionPlaces.NAVIGATION_BAR_TOOLBAR.equals(e.getPlace()))) {
        popup.showUnderneathOf(component);
      }
      else if (project == null) {
        popup.showInBestPositionFor(dataContext);
      }
      else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
    else {
      ExecutionManagerImpl.stopProcess(getRecentlyStartedContentDescriptor(dataContext));
    }
  }

  @Nullable
  private Pair<List<HandlerItem>, HandlerItem> getItemsList(Project project, List<? extends RunContentDescriptor> descriptors, RunContentDescriptor toSelect) {
    if (descriptors.isEmpty()) {
      return null;
    }

    List<HandlerItem> items = new ArrayList<>(descriptors.size());
    HandlerItem selected = null;
    for (final RunContentDescriptor descriptor : descriptors) {
      final ProcessHandler handler = descriptor.getProcessHandler();
      if (handler != null) {
        HandlerItem item = new HandlerItem(getDisplayName(project, descriptor), descriptor.getIcon(), false) {
          @Override
          void stop() {
            ExecutionManagerImpl.stopProcess(descriptor);
          }
        };
        items.add(item);
        if (descriptor == toSelect) {
          selected = item;
        }
      }
    }

    return Pair.create(items, selected);
  }

  @BuildEventsNls.Title
  protected String getDisplayName(final Project project, final RunContentDescriptor descriptor) {
    return descriptor.getDisplayName();
  }

  @Nullable
  static RunContentDescriptor getRecentlyStartedContentDescriptor(@NotNull DataContext dataContext) {
    final RunContentDescriptor contentDescriptor = LangDataKeys.RUN_CONTENT_DESCRIPTOR.getData(dataContext);
    if (contentDescriptor != null) {
      // toolwindow case
      return contentDescriptor;
    }
    else {
      // main menu toolbar
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      return project == null ? null : RunContentManager.getInstance(project).getSelectedContent();
    }
  }

  @NotNull
  private static List<RunContentDescriptor> getActiveStoppableDescriptors(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    List<RunContentDescriptor> runningProcesses = project == null ? Collections.emptyList() : ExecutionManagerImpl.getAllDescriptors(project);
    if (runningProcesses.isEmpty()) {
      return Collections.emptyList();
    }

    List<RunContentDescriptor> activeDescriptors = new SmartList<>();
    for (RunContentDescriptor descriptor : runningProcesses) {
      if (canBeStopped(descriptor)) {
        activeDescriptors.add(descriptor);
      }
    }
    return activeDescriptors;
  }

  private static boolean canBeStopped(@Nullable RunContentDescriptor descriptor) {
    @Nullable ProcessHandler processHandler = descriptor != null ? descriptor.getProcessHandler() : null;
    return processHandler != null && !processHandler.isProcessTerminated()
           && (!processHandler.isProcessTerminating()
               || processHandler instanceof KillableProcess && ((KillableProcess)processHandler).canKillProcess());
  }

  abstract static class HandlerItem {
    final @Nls String displayName;
    final Icon icon;
    final boolean hasSeparator;

    HandlerItem(@Nls String displayName, Icon icon, boolean hasSeparator) {
      this.displayName = displayName;
      this.icon = icon;
      this.hasSeparator = hasSeparator;
    }

    public String toString() {
      return displayName;
    }

    abstract void stop();
  }
}
