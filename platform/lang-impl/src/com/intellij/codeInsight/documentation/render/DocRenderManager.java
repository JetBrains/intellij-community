// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

import static com.intellij.codeWithMe.ClientId.withClientId;

public final class DocRenderManager {
  private static final Key<Boolean> DOC_RENDER_ENABLED = Key.create("doc.render.enabled");

  /**
   * Allows overriding global doc comments rendering setting for a specific editor. Passing {@code null} as {@code value} makes editor use
   * the global setting again.
   */
  public static void setDocRenderingEnabled(@NotNull Editor editor, @Nullable Boolean value) {
    ThreadingAssertions.assertEventDispatchThread();
    boolean enabledBefore = isDocRenderingEnabled(editor);
    editor.putUserData(DOC_RENDER_ENABLED, value);
    boolean enabledAfter = isDocRenderingEnabled(editor);
    if (enabledAfter != enabledBefore) {
      resetEditorToDefaultState(editor);
    }
  }

  /**
   * Tells whether doc comment rendering is enabled for a specific editor.
   *
   * @see #setDocRenderingEnabled(Editor, Boolean)
   */
  public static boolean isDocRenderingEnabled(@NotNull Editor editor) {
    if (editor.getEditorKind() == EditorKind.DIFF) return false;
    Boolean value = editor.getUserData(DOC_RENDER_ENABLED);
    boolean enabled;
    try (AccessToken ignored = withClientId(ClientEditorManager.getClientId(editor))) {
      enabled = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled();
    }
    return value == null ? enabled : value;
  }

  /**
   * Sets all doc comments to their default state (rendered or not rendered) for all opened editors.
   *
   * @see #isDocRenderingEnabled(Editor)
   */
  @RequiresEdt
  public static void resetAllEditorsToDefaultState() {
    for (Iterator<Editor> it = ClientEditorManager.Companion.getCurrentInstance().editorsSequence().iterator(); it.hasNext(); ) {
      Editor editor = it.next();
      DocRenderItemManager.getInstance().resetToDefaultState(editor);
      DocRenderPassFactory.forceRefreshOnNextPass(editor);
    }
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DaemonCodeAnalyzerEx.getInstanceEx(project).restart("DocRenderManager.resetAllEditorsToDefaultState");
    }
  }

  /**
   * Sets all doc comments to their default state (rendered or not rendered) in the specified editor.
   *
   * @see #isDocRenderingEnabled(Editor)
   */
  @RequiresEdt
  public static void resetEditorToDefaultState(@NotNull Editor editor) {
    DocRenderItemManager.getInstance().resetToDefaultState(editor);
    DocRenderPassFactory.forceRefreshOnNextPass(editor);
    Project project = editor.getProject();
    if (project != null) {
      DaemonCodeAnalyzerEx.getInstanceEx(project).restart("DocRenderManager.resetEditorToDefaultState");
    }
  }
}
