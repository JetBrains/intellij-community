// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class KeymapManager {
  public static final String DEFAULT_IDEA_KEYMAP = "$default";
  public static final String MAC_OS_X_KEYMAP = "Mac OS X";
  public static final String MAC_OS_X_10_5_PLUS_KEYMAP = "Mac OS X 10.5+";
  public static final String X_WINDOW_KEYMAP = "Default for XWin";
  public static final String KDE_KEYMAP = "Default for KDE";
  public static final String GNOME_KEYMAP = "Default for GNOME";

  @ApiStatus.Internal
  public static final String NET_BEANS_6_5 = "NetBeans 6.5";
  @ApiStatus.Internal
  public static final String ECLIPSE = "Eclipse";
  @ApiStatus.Internal
  public static final String ECLIPSE_MAC_OS_X = "Eclipse (Mac OS X)";
  @ApiStatus.Internal
  public static final String EMACS = "Emacs";
  @ApiStatus.Internal
  public static final String MAC_OS_SYSTEM_SHORTCUTS = "macOS System Shortcuts";
  @ApiStatus.Internal
  public static final String QT_CREATOR = "QtCreator";
  @ApiStatus.Internal
  public static final String QT_CREATOR_MAC_OS_X = "QtCreator (Mac OS X)";
  @ApiStatus.Internal
  public static final String RESHARPER = "ReSharper";
  @ApiStatus.Internal
  public static final String RESHARPER_OSX = "ReSharper OSX";
  @ApiStatus.Internal
  public static final String SUBLIME_TEXT = "Sublime Text";
  @ApiStatus.Internal
  public static final String SUBLIME_TEXT_MAC_OS_X = "Sublime Text (Mac OS X)";
  @ApiStatus.Internal
  public static final String TEXT_MATE = "TextMate";
  @ApiStatus.Internal
  public static final String VISUAL_ASSIST = "Visual Assist";
  @ApiStatus.Internal
  public static final String VISUAL_ASSIST_OSX = "Visual Assist OSX";
  @ApiStatus.Internal
  public static final String VISUAL_STUDIO = "Visual Studio";
  @ApiStatus.Internal
  public static final String VISUAL_STUDIO_2022 = "Visual Studio 2022";
  @ApiStatus.Internal
  public static final String VISUAL_STUDIO_OSX = "Visual Studio OSX";
  @ApiStatus.Internal
  public static final String VISUAL_STUDIO_FOR_MAC = "Visual Studio for Mac";
  @ApiStatus.Internal
  public static final String VSCODE = "VSCode";
  @ApiStatus.Internal
  public static final String VSCODE_OSX = "VSCode OSX";
  @ApiStatus.Internal
  public static final String XCODE = "Xcode";

  public abstract @NotNull Keymap getActiveKeymap();

  public abstract @Nullable Keymap getKeymap(@NotNull String name);

  private static volatile KeymapManager INSTANCE;

  public static KeymapManager getInstance() {
    Application app = ApplicationManager.getApplication();
    if (app == null || !LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred()) {
      return null;
    }

    KeymapManager instance = INSTANCE;
    if (instance == null) {
      instance = app.getService(KeymapManager.class);
      INSTANCE = instance;
    }
    return instance;
  }

  /**
   * @deprecated use {@link KeymapManagerListener#TOPIC} instead
   */
  @Deprecated(forRemoval = true)
  public abstract void removeKeymapManagerListener(@NotNull KeymapManagerListener listener);
}
