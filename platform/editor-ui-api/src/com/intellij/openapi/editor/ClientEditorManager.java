// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/**
 * Manages editors for particular clients. Take a look a {@link ClientSession}
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public final class ClientEditorManager {
  public static @NotNull ClientEditorManager getCurrentInstance() {
    return ApplicationManager.getApplication().getService(ClientEditorManager.class);
  }

  public static @NotNull List<ClientEditorManager> getAllInstances() {
    return ApplicationManager.getApplication().getServices(ClientEditorManager.class, true);
  }

  /**
   * @return clientId of a user that the editor corresponds to.
   */
  public static @Nullable ClientId getClientId(@NotNull Editor editor) {
    return CLIENT_ID.get(editor);
  }

  @ApiStatus.Internal
  public static void assignClientId(@NotNull Editor editor, @Nullable ClientId clientId) {
    CLIENT_ID.set(editor, clientId);
  }

  private static final Key<ClientId> CLIENT_ID = Key.create("CLIENT_ID");
  private final ClientId myClientId = ClientId.getCurrent();
  private final List<Editor> myEditors = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull
  public Stream<Editor> editors() {
    return myEditors.stream();
  }

  @NotNull
  public Stream<Editor> editors(@NotNull Document document, @Nullable Project project) {
    return editors()
      .filter(editor -> editor.getDocument().equals(document) && (project == null || project.equals(editor.getProject())));
  }

  public void editorCreated(@NotNull Editor editor) {
    if (!ClientId.isLocal(myClientId)) {
      CLIENT_ID.set(editor, myClientId);
    }
    myEditors.add(editor);
  }

  public boolean editorReleased(@NotNull Editor editor) {
    if (!ClientId.isLocal(myClientId)) {
      CLIENT_ID.set(editor, null);
    }
    return myEditors.remove(editor);
  }
}
