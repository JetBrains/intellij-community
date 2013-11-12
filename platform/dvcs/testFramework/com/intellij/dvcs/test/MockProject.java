/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.dvcs.test;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.components.BaseComponent;
import org.picocontainer.PicoContainer;
import com.intellij.util.messages.MessageBus;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 *
 * @author Kirill Likhodedov
 */
public class MockProject implements Project {

  private final String myProjectDir;

  public MockProject(String projectDir) {
    myProjectDir = projectDir;
  }

  @NotNull
  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile getBaseDir() {
    return new MockVirtualFile(myProjectDir);
  }

  @Override
  public String getBasePath() {
    return myProjectDir;
  }

  @Override
  public VirtualFile getProjectFile() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getProjectFilePath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getPresentableUrl() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile getWorkspaceFile() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getLocationHash() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void save() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isOpen() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInitialized() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @Override
  public BaseComponent getComponent(@NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementationIfAbsent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasComponent(@NotNull Class interfaceClass) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public <T> T[] getComponents(@NotNull Class<T> baseClass) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public MessageBus getMessageBus() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Condition getDisposed() {
    return Condition.FALSE;
  }

  @Override
  public void dispose() {
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
    throw new UnsupportedOperationException();
  }
}
