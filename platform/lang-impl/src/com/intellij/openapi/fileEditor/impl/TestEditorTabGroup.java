// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;

public final class TestEditorTabGroup {
  private final String name;

  private final LinkedHashMap<VirtualFile, Pair<FileEditor, FileEditorProvider>> myOpenedTabs = new LinkedHashMap<>();
  private VirtualFile myOpenedFile;

  public TestEditorTabGroup(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void openTab(VirtualFile virtualFile, FileEditor fileEditor, FileEditorProvider fileEditorProvider) {
    ThreadingAssertions.assertEventDispatchThread();

    myOpenedTabs.put(virtualFile, Pair.pair(fileEditor, fileEditorProvider));
    myOpenedFile = virtualFile;
  }

  public @Nullable Pair<FileEditor, FileEditorProvider> getOpenedEditor(){
    VirtualFile openedFile = getOpenedFile();
    if (openedFile == null) {
      return null;
    }

    return myOpenedTabs.get(openedFile);
  }

  public @Nullable VirtualFile getOpenedFile() {
    ThreadingAssertions.assertEventDispatchThread();
    return myOpenedFile;
  }

  public void setOpenedFile(VirtualFile file) {
    ThreadingAssertions.assertEventDispatchThread();
    myOpenedFile = file;
  }

  public void closeTab(VirtualFile virtualFile) {
    ThreadingAssertions.assertEventDispatchThread();
    myOpenedFile = null;
    myOpenedTabs.remove(virtualFile);
  }

  public @Nullable Pair<FileEditor, FileEditorProvider> getEditorAndProvider(VirtualFile file) {
    return myOpenedTabs.get(file);
  }

  public boolean contains(VirtualFile file) {
    return myOpenedTabs.containsKey(file);
  }

  public int getTabCount() {
    return myOpenedTabs.size();
  }
}
