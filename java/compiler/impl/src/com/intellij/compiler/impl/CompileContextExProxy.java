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
package com.intellij.compiler.impl;

import com.intellij.compiler.make.DependencyCache;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 4, 2007
 */
public class CompileContextExProxy implements CompileContextEx {
  private final CompileContextEx myDelegate;

  public CompileContextExProxy(CompileContextEx delegate) {
    myDelegate = delegate;
  }

  public Project getProject() {
    return myDelegate.getProject();
  }

  public DependencyCache getDependencyCache() {
    return myDelegate.getDependencyCache();
  }

  public VirtualFile getSourceFileByOutputFile(final VirtualFile outputFile) {
    return myDelegate.getSourceFileByOutputFile(outputFile);
  }

  public void addMessage(final CompilerMessage message) {
    myDelegate.addMessage(message);
  }

  @NotNull
  public Set<VirtualFile> getTestOutputDirectories() {
    return myDelegate.getTestOutputDirectories();
  }

  public boolean isInTestSourceContent(@NotNull final VirtualFile fileOrDir) {
    return myDelegate.isInTestSourceContent(fileOrDir);
  }

  public boolean isInSourceContent(@NotNull final VirtualFile fileOrDir) {
    return myDelegate.isInSourceContent(fileOrDir);
  }

  public void addScope(final CompileScope additionalScope) {
    myDelegate.addScope(additionalScope);
  }

  public void addMessage(final CompilerMessageCategory category,
                         final String message, @Nullable final String url, final int lineNum, final int columnNum) {
    myDelegate.addMessage(category, message, url, lineNum, columnNum);
  }

  public void addMessage(final CompilerMessageCategory category, final String message, @Nullable final String url,
                         final int lineNum,
                         final int columnNum,
                         final Navigatable navigatable) {
    myDelegate.addMessage(category, message, url, lineNum, columnNum, navigatable);
  }

  public CompilerMessage[] getMessages(final CompilerMessageCategory category) {
    return myDelegate.getMessages(category);
  }

  public int getMessageCount(final CompilerMessageCategory category) {
    return myDelegate.getMessageCount(category);
  }

  public ProgressIndicator getProgressIndicator() {
    return myDelegate.getProgressIndicator();
  }

  public CompileScope getCompileScope() {
    return myDelegate.getCompileScope();
  }

  public CompileScope getProjectCompileScope() {
    return myDelegate.getProjectCompileScope();
  }

  public void requestRebuildNextTime(final String message) {
    myDelegate.requestRebuildNextTime(message);
  }

  public Module getModuleByFile(final VirtualFile file) {
    return myDelegate.getModuleByFile(file);
  }

  public VirtualFile[] getSourceRoots(final Module module) {
    return myDelegate.getSourceRoots(module);
  }

  public VirtualFile[] getAllOutputDirectories() {
    return myDelegate.getAllOutputDirectories();
  }

  public VirtualFile getModuleOutputDirectory(final Module module) {
    return myDelegate.getModuleOutputDirectory(module);
  }

  public VirtualFile getModuleOutputDirectoryForTests(final Module module) {
    return myDelegate.getModuleOutputDirectoryForTests(module);
  }

  public boolean isMake() {
    return myDelegate.isMake();
  }

  public boolean isRebuild() {
    return myDelegate.isRebuild();
  }

  public <T> T getUserData(@NotNull final Key<T> key) {
    return myDelegate.getUserData(key);
  }

  public <T> void putUserData(@NotNull final Key<T> key, final T value) {
    myDelegate.putUserData(key, value);
  }

  public void recalculateOutputDirs() {
    myDelegate.recalculateOutputDirs();
  }

  public void markGenerated(Collection<VirtualFile> files) {
    myDelegate.markGenerated(files);
  }

  public boolean isGenerated(VirtualFile file) {
    return myDelegate.isGenerated(file);
  }

  public long getStartCompilationStamp() {
    return myDelegate.getStartCompilationStamp();
  }

  public void updateZippedOuput(String outputDir, String relativePath) throws IOException {
    myDelegate.updateZippedOuput(outputDir, relativePath);
  }

  public void commitZipFiles() {
    myDelegate.commitZipFiles();
  }

  public void commitZip(String outputDir) throws IOException {
    myDelegate.commitZip(outputDir);
  }

  public void assignModule(@NotNull VirtualFile root, @NotNull Module module, boolean isTestSource) {
    myDelegate.assignModule(root, module, isTestSource);
  }
}
