package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;

public class TestEditorTabGroup {
  private final String name;

  private final LinkedHashMap<VirtualFile, Pair<FileEditor, FileEditorProvider>> myOpenedTabs = new LinkedHashMap<>();
  private VirtualFile myOpenedfile;

  public TestEditorTabGroup(String name) {
    this.name = name;
  }

  public String Name() {
    return name;
  }

  public void openTab(VirtualFile virtualFile, FileEditor fileEditor, FileEditorProvider fileEditorProvider) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myOpenedTabs.put(virtualFile, Pair.pair(fileEditor, fileEditorProvider));
    myOpenedfile = virtualFile;
  }

  @Nullable
  public Pair<FileEditor, FileEditorProvider> getOpenedEditor(){
    VirtualFile openedFile = getOpenedFile();
    if (openedFile == null) {
      return null;
    }

    return myOpenedTabs.get(openedFile);
  }

  @Nullable
  public VirtualFile getOpenedFile() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myOpenedfile;
  }

  public void closeTab(VirtualFile virtualFile) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myOpenedfile = null;
    myOpenedTabs.remove(virtualFile);
  }

  @Nullable
  public Pair<FileEditor, FileEditorProvider> getEditorAndProvider(VirtualFile file) {
    return myOpenedTabs.get(file);
  }

  public boolean contains(VirtualFile file) {
    return myOpenedTabs.containsKey(file);
  }

  public int getTabCount() {
    return myOpenedTabs.size();
  }
}
