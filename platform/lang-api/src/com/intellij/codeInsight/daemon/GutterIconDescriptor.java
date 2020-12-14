// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Allows user to configure visible gutter icons.
 *
 * @author Dmitry Avdeev
 */
public abstract class GutterIconDescriptor {

  /**
   * Human-readable provider name for UI.
   *
   * @return null if no configuration needed
   */
  @Nullable("null means disabled")
  @GutterName
  public abstract String getName();

  /**
   * Icon in size 12x12.
   * See <a href="https://jetbrains.org/intellij/sdk/docs/reference_guide/work_with_icons_and_images.html">Icons and Images</a>.
   */
  @Nullable
  public Icon getIcon() {
    return null;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NonNls
  public String getId() {
    return getClass().getName();
  }

  public Option @NotNull [] getOptions() {
    return Option.NO_OPTIONS;
  }

  @Override
  public String toString() {
    return getName();
  }

  public static class Option extends GutterIconDescriptor {
    private static final Option[] NO_OPTIONS = new Option[0];

    private final String myId;
    private final @GutterName String myName;
    private final Icon myIcon;

    public Option(@NotNull String id,
                  @NotNull @GutterName String name,
                  Icon icon) {
      myId = id;
      myName = name;
      myIcon = icon;
    }

    public boolean isEnabled() {
      return LineMarkerSettings.getSettings().isEnabled(this);
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public String getId() {
      return myId;
    }
  }
}
