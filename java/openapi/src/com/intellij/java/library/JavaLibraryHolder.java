// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.library;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderEnumerator;
import org.jetbrains.annotations.NotNull;

import static com.intellij.java.library.JavaLibraryUtil.fillLibraries;

@Service(Service.Level.PROJECT)
public final class JavaLibraryHolder implements Disposable {

  private final Project myProject;
  private volatile Libraries myValue;

  @Override
  public void dispose() {
    // do nothing
  }

  public static @NotNull JavaLibraryHolder getInstance(@NotNull Project project) {
    return project.getService(JavaLibraryHolder.class);
  }

  public JavaLibraryHolder(@NotNull Project project) {
    myProject = project;

    project.getMessageBus().connect(this)
      .subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
        @Override
        public void rootsChanged(@NotNull ModuleRootEvent event) {
          myValue = null;
        }
      });
  }

  Libraries getLibraries() {
    if (myValue == null) {
      myValue = fillLibraries(OrderEnumerator.orderEntries(myProject), true);
    }
    return myValue;
  }
}