// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.action;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.module.Module;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class ExternalSystemActionUtil {

  public static void executeAction(final String actionId, final InputEvent e) {
    executeAction(actionId, "", e);
  }

  public static void executeAction(final String actionId, @NotNull final String place, final InputEvent e) {
    final ActionManager actionManager = ActionManager.getInstance();
    final AnAction action = actionManager.getAction(actionId);
    if (action != null) {
      ActionUtil.invokeAction(action, e.getComponent(), place, e, null);
    }
  }

  @Nullable
  public static Module getModule(DataContext context) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(context);
    return module != null ? module : LangDataKeys.MODULE_CONTEXT.getData(context);
  }

  public static <E> void setElements(ElementsChooser<E> chooser, Collection<? extends E> all, Collection<E> selected, Comparator<? super E> comparator) {
    List<E> selection = chooser.getSelectedElements();
    chooser.clear();
    Collection<E> sorted = new TreeSet<>(comparator);
    sorted.addAll(all);
    for (E element : sorted) {
      chooser.addElement(element, selected.contains(element));
    }
    chooser.selectElements(selection);
  }

  @NotNull
  public static ExternalTaskExecutionInfo buildTaskInfo(@NotNull TaskData task) {
    ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    settings.setExternalProjectPath(task.getLinkedExternalProjectPath());
    settings.setTaskNames(Collections.singletonList(task.getName()));
    settings.setTaskDescriptions(Collections.singletonList(task.getDescription()));
    settings.setExternalSystemIdString(task.getOwner().toString());
    return new ExternalTaskExecutionInfo(settings, DefaultRunExecutor.EXECUTOR_ID);
  }
}