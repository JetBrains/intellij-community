// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public final class FileTypeEditorHighlighterProviders extends FileTypeExtension<EditorHighlighterProvider> {
  public static final ExtensionPointName<KeyedLazyInstance<EditorHighlighterProvider>> EP_NAME = ExtensionPointName.create("com.intellij.editorHighlighterProvider");
  public static final FileTypeEditorHighlighterProviders INSTANCE = new FileTypeEditorHighlighterProviders();

  private boolean myEPListenerAdded = false;

  private FileTypeEditorHighlighterProviders() {
    super(EP_NAME);
  }

  @NotNull
  @Override
  protected List<EditorHighlighterProvider> buildExtensions(@NotNull String stringKey, @NotNull final FileType key) {
    List<EditorHighlighterProvider> fromEP = super.buildExtensions(stringKey, key);
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
      return Collections.singletonList(defaultProvider);
    }
    return fromEP;
  }

  private synchronized void checkAddEPListener() {
    if (!myEPListenerAdded) {
      myEPListenerAdded = true;

      SyntaxHighlighter.EP_NAME.addExtensionPointListener(new ExtensionPointListener<KeyedFactoryEPBean>() {
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
      }, null);
    }
  }
}
