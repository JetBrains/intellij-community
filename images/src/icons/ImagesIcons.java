// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class ImagesIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, ImagesIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconLoader.getIcon(path, clazz);
  }

  /**
   * 16x16
   */
  public static final Icon ImagesFileType = load("/org/intellij/images/icons/ImagesFileType.svg");
  /**
   * 75x86
   */
  public static final Icon ThumbnailBlank = load("/org/intellij/images/icons/ThumbnailBlank.png");
  /**
   * 75x82
   */
  public static final Icon ThumbnailDirectory = load("/org/intellij/images/icons/ThumbnailDirectory.png");
  /**
   * 13x13
   */
  public static final Icon ThumbnailToolWindow = load("/org/intellij/images/icons/ThumbnailToolWindow.svg");
  /**
   * 16x16
   */
  public static final Icon ToggleTransparencyChessboard = load("/org/intellij/images/icons/ToggleTransparencyChessboard.svg");

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Graph.Grid */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon ToggleGrid = load("/graph/grid.svg", com.intellij.icons.AllIcons.class);
}
