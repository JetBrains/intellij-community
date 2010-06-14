/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class UnitTestMode implements ProjectComponent {
  private final Set<Object> myNotInUnitTestMode;

  public UnitTestMode() {
    myNotInUnitTestMode = new HashSet<Object>();
  }

  public static UnitTestMode getInstance(final Project project) {
    return project.getComponent(UnitTestMode.class);
  }

  public void register(final Object obj) {
    myNotInUnitTestMode.add(obj);
  }

  public boolean isInUnitTestMode(final Object key) {
    if (myNotInUnitTestMode.contains(key)) return false;
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
