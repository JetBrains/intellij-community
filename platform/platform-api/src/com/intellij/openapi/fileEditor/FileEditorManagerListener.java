/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface FileEditorManagerListener extends EventListener{
  Topic<FileEditorManagerListener> FILE_EDITOR_MANAGER =
    new Topic<>("file editor events", FileEditorManagerListener.class, Topic.BroadcastDirection.TO_PARENT);

  void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file);

  void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file);

  void selectionChanged(@NotNull FileEditorManagerEvent event);

  interface Before extends EventListener {
    Topic<Before> FILE_EDITOR_MANAGER =
      new Topic<>("file editor before events", Before.class, Topic.BroadcastDirection.TO_PARENT);

    void beforeFileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file);
    void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file);

    class Adapter implements Before {
      @Override
      public void beforeFileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) { }

      @Override
      public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) { }
    }
  }
}