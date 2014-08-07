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

/**
 * @author cdr
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * used to override JdkHome location in order to provide correct paths
 */
public final class MockJdkWrapper implements Sdk {
  private final String myHomePath;
  private final Sdk myDelegate;

  public MockJdkWrapper(String homePath, @NotNull Sdk delegate) {
    myHomePath = homePath;
    myDelegate = delegate;
  }

  public VirtualFile getHomeDirectory() {
    return LocalFileSystem.getInstance().findFileByIoFile(new File(getHomePath()));
  }

  public String getHomePath() {
    final String homePath = FileUtil.toSystemDependentName(myHomePath == null ? myDelegate.getHomePath() : myHomePath);
    return StringUtil.trimEnd(homePath, File.separator);
  }

  @NotNull
  public SdkTypeId getSdkType() {
    return myDelegate.getSdkType();
  }

  @NotNull
  public String getName() {
    return myDelegate.getName();
  }

  public String getVersionString() {
    return myDelegate.getVersionString();
  }

  @NotNull
  public RootProvider getRootProvider() {
    return myDelegate.getRootProvider();
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    return myDelegate.getUserData(key);
  }

  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDelegate.putUserData(key, value);
  }

  @NotNull
  public Object clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }

  public SdkAdditionalData getSdkAdditionalData() {
    return null;
  }

  @NotNull
  public SdkModificator getSdkModificator() {
    return myDelegate.getSdkModificator();
  }

  public Sdk getDelegate() {
    return myDelegate;
  }
}