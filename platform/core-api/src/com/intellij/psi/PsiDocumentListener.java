// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiDocumentListener {

  @Topic.ProjectLevel
  Topic<PsiDocumentListener> TOPIC = new Topic<>(PsiDocumentListener.class, Topic.BroadcastDirection.NONE);

  /**
   * Called when a document instance is created for a file.
   *
   * @param document the created document instance.
   * @param psiFile the file for which the document was created.
   * @see PsiDocumentManager#getDocument(PsiFile)
   */
  void documentCreated(@NotNull Document document, @Nullable PsiFile psiFile, @NotNull Project project);

  /**
   * Called when a file instance is created for a document.
   *
   * @param file the created file instance.
   * @param document the document for which the file was created.
   * @see PsiDocumentManager#getDocument(PsiFile)
   */
  default void fileCreated(@NotNull PsiFile file, @NotNull Document document) {
  }
}
