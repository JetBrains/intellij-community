package com.intellij.openapi.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;

public class DummyCompileContext implements CompileContext {
  private DummyCompileContext() {
  }

  private static final DummyCompileContext OUR_INSTANCE = new DummyCompileContext();

  public static DummyCompileContext getInstance() {
    return OUR_INSTANCE;
  }
  
  public void addMessage(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum) {
  }

  public CompilerMessage[] getMessages(CompilerMessageCategory category) {
    return new CompilerMessage[0];
  }

  public int getMessageCount(CompilerMessageCategory category) {
    return 0;
  }

  public ProgressIndicator getProgressIndicator() {
    return null;
  }

  public CompileScope getCompileScope() {
    return null;
  }

  public void requestRebuildNextTime(String message) {
  }

  public Module getModuleByFile(VirtualFile file) {
    return null;
  }

  public VirtualFile[] getSourceRoots(Module module) {
    return VirtualFile.EMPTY_ARRAY;
  }

  public VirtualFile[] getAllOutputDirectories() {
    return VirtualFile.EMPTY_ARRAY;
  }

  public VirtualFile getModuleOutputDirectory(Module module) {
    return null;
  }

  public VirtualFile getModuleOutputDirectoryForTests(Module module) {
    return null;
  }

  public <T> T getUserData(Key<T> key) {
    return null;
  }

  public <T> void putUserData(Key<T> key, T value) {
  }
}