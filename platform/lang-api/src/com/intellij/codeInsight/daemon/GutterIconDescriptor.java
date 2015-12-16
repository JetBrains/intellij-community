/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Allows user to configure visible gutter icons.
 *
 * @author Dmitry Avdeev
 * @since 144
 */
public abstract class GutterIconDescriptor {

  protected static final Option[] NO_OPTIONS = new Option[0];

  /**
   * Human-readable provider name for UI.
   *
   * @return null if no configuration needed
   */
  @Nullable("null means disabled")
  public abstract String getName();

  @Nullable
  public Icon getIcon() {
    return null;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public String getId() {
    return getClass().getName();
  }

  public Option[] getOptions() {
    return NO_OPTIONS;
  }

  @Override
  public String toString() {
    return getName();
  }


  public static class Option extends GutterIconDescriptor {

    private final String myId;
    private final String myName;
    private final Icon myIcon;

    public Option(@NotNull String id, @NotNull String name, Icon icon) {
      myId = id;
      myName = name;
      myIcon = icon;
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
