// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class DumpExtensionsAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<ExtensionsArea> areas = new ArrayList<>();
    areas.add(ApplicationManager.getApplication().getExtensionArea());
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      areas.add(project.getExtensionArea());
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      if (modules.length > 0) {
        areas.add(modules[0].getExtensionArea());
      }
    }
    System.out.print(areas.size() + " extension areas: ");
    for (ExtensionsArea area : areas) {
      System.out.print(area.toString() + " ");
    }
    System.out.println("\n");

    List<ExtensionPoint<?>> points = new ArrayList<>();
    for (ExtensionsArea area : areas) {
      points.addAll(((ExtensionsAreaImpl)area).extensionPoints.values());
    }
    System.out.println(points.size() + " extension points: ");
    for (ExtensionPoint<?> point : points) {
      System.out.println(" " + ((ExtensionPointImpl<?>)point).getName());
    }

    List<Object> extensions = new ArrayList<>();
    for (ExtensionPoint<?> point : points) {
      extensions.addAll(Arrays.asList(point.getExtensions()));
    }
    System.out.println("\n" + extensions.size() + " extensions:");
    for (Object extension : extensions) {
      if (extension instanceof Configurable) {
        System.out.println("!!!! Configurable extension found. Kill it !!!");
      }
      System.out.println(extension);
    }
  }
}
