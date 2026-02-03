// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.execution.configurations.ConfigurationWithAlternativeJre;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class AlternativeJreClassFinder extends NonClasspathClassFinder {
  public AlternativeJreClassFinder(Project project) {
    super(project);

    project.getMessageBus().connect().subscribe(DebuggerManagerListener.TOPIC, new DebuggerManagerListener() {
      @Override
      public void sessionCreated(DebuggerSession session) {
        clearCache();
      }

      @Override
      public void sessionRemoved(DebuggerSession session) {
        clearCache();
      }
    });
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    Collection<DebuggerSession> sessions = DebuggerManagerEx.getInstanceEx(myProject).getSessions();
    if (sessions.isEmpty()) {
      return Collections.emptyList();
    }
    List<VirtualFile> res = new SmartList<>();
    for (DebuggerSession session : sessions) {
      Sdk jre = session.getAlternativeJre();
      if (jre != null) {
        res.addAll(getClassRoots(jre));
      }
    }
    return res;
  }

  public static @Nullable Sdk getAlternativeJre(RunProfile profile) {
    if (profile instanceof ConfigurationWithAlternativeJre appConfig && appConfig.isAlternativeJrePathEnabled()) {
      String path = appConfig.getAlternativeJrePath();
      return path == null ? null : ProjectJdkTable.getInstance().findJdk(path);
    }
    return null;
  }

  private static @NotNull Collection<VirtualFile> getClassRoots(@NotNull Sdk jre) {
    return Arrays.asList(jre.getRootProvider().getFiles(OrderRootType.CLASSES));
  }

  public static @NotNull Collection<VirtualFile> getSourceRoots(@NotNull Sdk jre) {
    return Arrays.asList(jre.getRootProvider().getFiles(OrderRootType.SOURCES));
  }

  public static @NotNull GlobalSearchScope getSearchScope(@NotNull Sdk jre) {
    return new NonClasspathDirectoriesScope(getClassRoots(jre));
  }
}
