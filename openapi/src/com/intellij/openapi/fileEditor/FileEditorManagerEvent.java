/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.EventObject;

public final class FileEditorManagerEvent extends EventObject {
  private final VirtualFile myOldFile;
  private final FileEditor myOldEditor;
  private final VirtualFile myNewFile;
  private final FileEditor myNewEditor;

  public FileEditorManagerEvent(
    FileEditorManager source,
    VirtualFile oldFile,
    FileEditor oldEditor,
    VirtualFile newFile,
    FileEditor newEditor
  ){
    super(source);
    myOldFile = oldFile;
    myOldEditor = oldEditor;
    myNewFile = newFile;
    myNewEditor = newEditor;
  }

  public FileEditorManager getManager(){
    return (FileEditorManager)getSource();
  }

  public VirtualFile getOldFile() {
    return myOldFile;
  }

  public VirtualFile getNewFile() {
    return myNewFile;
  }

  public FileEditor getOldEditor() {
    return myOldEditor;
  }

  public FileEditor getNewEditor() {
    return myNewEditor;
  }
}