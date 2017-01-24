/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;

/**
 * Created by Kirill.Skrygan on 1/21/2016.
 */

/**
 * Test component that allows to test FileEditorManagerEvent and other events in the test environment
 * Emulates changing selection from one tab to another, by throwing appropriate events to the listeners
 */
public class TestEditorSplitter {
  private final HashMap<String, TestTabWell> myTabWellsMap = new HashMap<String, TestTabWell>();
  private static final String Default = "Default";

  public TestEditorSplitter(){
    myTabWellsMap.put(Default, new TestTabWell(Default));
  }

  private TestTabWell getDefaultTabWell(){
    return myTabWellsMap.get(Default);
  }

  public void openAndFocusTab(VirtualFile virtualFile, FileEditor fileEditor, FileEditorProvider provider) {
    getDefaultTabWell().openTab(virtualFile, fileEditor, provider);
  }

  /**
   * all these necessary parameters can be retrieved by TestEditorManagerImpl
   * @param virtualFile virtual file that should be opened in the specific tab well
   * @param fileEditor associated fileEditor
   * @param specificTabWell id of a tab well where the specified file should be opened
   */
  @TestOnly
  public void openAndFocusTab(VirtualFile virtualFile, FileEditor fileEditor, String specificTabWell, FileEditorProvider provider) {
    TestTabWell result = myTabWellsMap.get(specificTabWell);
    if (result == null) {
      result = new TestTabWell(specificTabWell);
      myTabWellsMap.put(specificTabWell, result);
    }

    result.openTab(virtualFile, fileEditor, provider);
  }

  @Nullable
  public FileEditor getFocusedFileEditor() {
    Pair<FileEditor, FileEditorProvider> openedEditor = getDefaultTabWell().getOpenedEditor();
    if(openedEditor == null)
      return null;

    return openedEditor.first;
  }

  @Nullable
  public FileEditorProvider getProviderFromFocused() {
    Pair<FileEditor, FileEditorProvider> openedEditor = getDefaultTabWell().getOpenedEditor();
    if(openedEditor == null)
      return null;

    return openedEditor.second;
  }

  public VirtualFile getFocusedFile() {
    return getDefaultTabWell().getOpenedFile();
  }

  public void closeFile(VirtualFile file) {
    getDefaultTabWell().closeTab(file);
  }

  @Nullable
  public Pair<FileEditor, FileEditorProvider> getEditorAndProvider(VirtualFile file) {
    return getDefaultTabWell().getEditorAndProvider(file);
  }
}
