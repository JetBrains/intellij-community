/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.dvcs.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.components.BaseComponent
import org.picocontainer.PicoContainer
import com.intellij.util.messages.MessageBus
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.NotNull

/**
 * 
 * @author Kirill Likhodedov
 */
class MockProject implements Project {

  String myProjectDir

  MockProject(String projectDir) {
    myProjectDir = projectDir
  }

  @NotNull
  @Override
  String getName() {
    throw new UnsupportedOperationException()
  }

  @Override
  VirtualFile getBaseDir() {
    return new MockVirtualFile(myProjectDir)
  }

  @Override
  String getBasePath() {
    return myProjectDir
  }

  @Override
  VirtualFile getProjectFile() {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  String getProjectFilePath() {
    throw new UnsupportedOperationException()
  }

  @Override
  String getPresentableUrl() {
    throw new UnsupportedOperationException()
  }

  @Override
  VirtualFile getWorkspaceFile() {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  String getLocationHash() {
    throw new UnsupportedOperationException()
  }

  @Override
  String getLocation() {
    throw new UnsupportedOperationException()
  }

  @Override
  void save() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isOpen() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isInitialized() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isDefault() {
    false
  }

  @Override
  BaseComponent getComponent(String name) {
    throw new UnsupportedOperationException()
  }

  @Override
  def <T> T getComponent(Class<T> interfaceClass) {
    throw new UnsupportedOperationException()
  }

  @Override
  def <T> T getComponent(Class<T> interfaceClass, T defaultImplementationIfAbsent) {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean hasComponent(@NotNull Class interfaceClass) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  def <T> T[] getComponents(Class<T> baseClass) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException()
  }

  @Override
  MessageBus getMessageBus() {
    null
  }

  @Override
  boolean isDisposed() {
    false
  }

  @Override
  def <T> T[] getExtensions(ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  Condition getDisposed() {
    Condition.FALSE
  }

  @Override
  void dispose() {
  }

  @Override
  def <T> T getUserData(Key<T> key) {
    throw new UnsupportedOperationException()
  }

  @Override
  def <T> void putUserData(Key<T> key, T value) {
    throw new UnsupportedOperationException()
  }
}
