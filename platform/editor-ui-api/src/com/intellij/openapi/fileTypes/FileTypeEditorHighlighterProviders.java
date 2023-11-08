// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.KeyedLazyInstance;
import kotlinx.collections.immutable.PersistentList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf;

public final class FileTypeEditorHighlighterProviders extends FileTypeExtension<EditorHighlighterProvider> {
  public static final ExtensionPointName<KeyedLazyInstance<EditorHighlighterProvider>> EP_NAME = ExtensionPointName.create("com.intellij.editorHighlighterProvider");
  public static final FileTypeEditorHighlighterProviders INSTANCE = new FileTypeEditorHighlighterProviders();

  private boolean myEPListenerAdded = false;

  private FileTypeEditorHighlighterProviders() {
    super(EP_NAME);
  }

  @Override
  protected @NotNull PersistentList<EditorHighlighterProvider> buildExtensions(@NotNull String stringKey, final @NotNull FileType key) {
    PersistentList<EditorHighlighterProvider> fromEP = super.buildExtensions(stringKey, key);
    if (fromEP.isEmpty()) {
      checkAddEPListener();
      EditorHighlighterProvider defaultProvider = new EditorHighlighterProvider() {
        @Override
        public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                      @NotNull FileType fileType,
                                                      @Nullable VirtualFile virtualFile,
                                                      @NotNull EditorColorsScheme colors) {
          return EditorHighlighterFactory.getInstance().createEditorHighlighter(
            SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, virtualFile), colors);
        }
      };
      return persistentListOf(defaultProvider);
    }
    return fromEP;
  }

  private synchronized void checkAddEPListener() {
    if (!myEPListenerAdded) {
      myEPListenerAdded = true;

      SyntaxHighlighter.EP_NAME.addExtensionPointListener(new MyEPListener(), null);
    }
  }

  private class MyEPListener implements ExtensionPointListener<KeyedFactoryEPBean>, ExtensionPointPriorityListener {
    @Override
    public void extensionAdded(@NotNull KeyedFactoryEPBean extension, @NotNull PluginDescriptor pluginDescriptor) {
      if (extension.key != null) {
        invalidateCacheForExtension(extension.key);
      }
    }

    @Override
    public void extensionRemoved(@NotNull KeyedFactoryEPBean extension, @NotNull PluginDescriptor pluginDescriptor) {
      if (extension.key != null) {
        invalidateCacheForExtension(extension.key);
      }
    }
  }
}
