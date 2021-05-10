// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

/**
 * Manages editors for particular clients. Take a look a {@link ClientSession}
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public class ClientEditorManager {
  public static @NotNull ClientEditorManager getCurrentInstance() {
    return ApplicationManager.getApplication().getService(ClientEditorManager.class);
  }

  public static @NotNull List<ClientEditorManager> getAllInstances() {
    return ApplicationManager.getApplication().getServices(ClientEditorManager.class, true);
  }

  private final List<Editor> myEditors = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull
  public Stream<Editor> editors() {
    return myEditors.stream();
  }

  public void editorCreated(@NotNull Editor editor) {
    myEditors.add(editor);
  }

  public boolean editorReleased(@NotNull Editor editor) {
    return myEditors.remove(editor);
  }
}
