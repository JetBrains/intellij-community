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

  public long getStartCompilationStamp() {
    return myDelegate.getStartCompilationStamp();
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

  public <T> T getUserData(final Key<T> key) {
    return myDelegate.getUserData(key);
  }

  public <T> void putUserData(final Key<T> key, final T value) {
    myDelegate.putUserData(key, value);
  }
}
