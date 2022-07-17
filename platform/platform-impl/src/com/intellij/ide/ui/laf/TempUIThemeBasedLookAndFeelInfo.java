// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.UITheme;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class TempUIThemeBasedLookAndFeelInfo extends UIThemeBasedLookAndFeelInfo {

  private static final Logger LOG = Logger.getInstance(TempUIThemeBasedLookAndFeelInfo.class);
  private static final @NonNls String ID = "Temp theme";

  private final @Nullable VirtualFile mySchemeFile;
  private final @Nullable UIManager.LookAndFeelInfo myPreviousLaf;

  public TempUIThemeBasedLookAndFeelInfo(@NotNull UITheme theme,
                                         @Nullable VirtualFile editorSchemeFile,
                                         @Nullable UIManager.LookAndFeelInfo previousLaf) {
    super(theme);
    assert ID.equals(theme.getId());

    mySchemeFile = editorSchemeFile;
    myPreviousLaf = previousLaf instanceof TempUIThemeBasedLookAndFeelInfo ?
                    ((TempUIThemeBasedLookAndFeelInfo)previousLaf).getPreviousLaf() :
                    previousLaf;
  }

  public @Nullable UIManager.LookAndFeelInfo getPreviousLaf() {
    return myPreviousLaf;
  }

  @Override
  protected @Nullable InputStream getResourceAsStream(@NotNull String path) {
    Path file = Path.of(path);
    if (Files.exists(file)) {
      try {
        return Files.newInputStream(file);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    return null;
  }

  @Override
  protected void installEditorScheme() {
    String name = getTheme().getEditorScheme();
    if (name != null && mySchemeFile != null) {
      EditorColorsManagerImpl cm = (EditorColorsManagerImpl)EditorColorsManager.getInstance();
      AbstractColorsScheme tmpScheme = new DefaultColorsScheme();
      boolean loaded = false;
      try {
        Element xml = JDOMUtil.load(mySchemeFile.getInputStream());
        String parentSchemeName = xml.getAttributeValue("parent_scheme", EditorColorsManager.DEFAULT_SCHEME_NAME);
        EditorColorsScheme parentScheme = EditorColorsManager.getInstance().getScheme(parentSchemeName);
        tmpScheme = new EditorColorsSchemeImpl(parentScheme);
        tmpScheme.readExternal(xml);
        loaded = true;
      }
      catch (Exception e) {
        LOG.warn(e);
      }

      if (loaded) {
        AbstractColorsScheme scheme = tmpScheme;
        EditorColorsManagerImpl.setTempScheme(scheme, mySchemeFile);
        cm.setGlobalScheme(scheme);
        MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect();
        connect.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
          @Override
          public void globalSchemeChange(@Nullable EditorColorsScheme editorColorsScheme) {
            if (editorColorsScheme == scheme || editorColorsScheme == null) {
              return;
            }
            cm.getSchemeManager().removeScheme(scheme);
            connect.disconnect();
          }
        });
      }
    }
  }

  public static @NotNull UITheme loadTempTheme(@NotNull InputStream stream,
                                               @NotNull IconPathPatcher patcher) throws IOException {
    UITheme theme = UITheme.loadFromJson(stream,
                                         ID,
                                         null,
                                         Function.identity());

    IconPathPatcher oldPatcher = theme.getPatcher();
    if (oldPatcher != null) {
      theme.setPatcher(new IconPathPatcher() {
        @Override
        public @Nullable String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
          String result = oldPatcher.patchPath(path, classLoader);
          return result != null ? patcher.patchPath(result, classLoader) : null;
        }

        @Override
        public @Nullable ClassLoader getContextClassLoader(@NotNull String path, @Nullable ClassLoader originalClassLoader) {
          return patcher.getContextClassLoader(path, originalClassLoader);
        }
      });
    }

    return theme;
  }
}
