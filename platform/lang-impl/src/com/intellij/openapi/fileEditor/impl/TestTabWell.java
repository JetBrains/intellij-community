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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Created by Kirill.Skrygan on 1/21/2016.
 */
public class TestTabWell {
  private String name;

  private final LinkedHashMap<VirtualFile, Pair<FileEditor, FileEditorProvider>> myOpenedTabs = new LinkedHashMap<VirtualFile, Pair<FileEditor, FileEditorProvider>>();

  public TestTabWell(String name) {
    this.name = name;
  }

  public String Name() {
    return name;
  }

  public void openTab(VirtualFile virtualFile, FileEditor fileEditor, FileEditorProvider fileEditorProvider) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myOpenedTabs.put(virtualFile, Pair.pair(fileEditor, fileEditorProvider));
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
    Iterator<VirtualFile> iterator = myOpenedTabs.keySet().iterator();
    VirtualFile lastElement = null;
    while (iterator.hasNext()) {
      lastElement = iterator.next();
    }
    return lastElement;
  }

  public void closeTab(VirtualFile virtualFile) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myOpenedTabs.remove(virtualFile);
  }

  @Nullable
  public Pair<FileEditor, FileEditorProvider> getEditorAndProvider(VirtualFile file) {
    return myOpenedTabs.get(file);
  }
}
