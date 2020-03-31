// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Represents presentation in target popup as follows:<br/>
 * <code>| $icon $presentable_text (in $location_text) spacer $right_text $right_icon |</code><br/>
 * Elements before spacer are aligned to the left, right text and right icon are aligned to the right.
 */
@Experimental
public interface TargetPopupPresentation {

  @Nullable
  default Icon getIcon() {
    return null;
  }

  @NotNull
  String getPresentableText();

  /**
   * Note that returned instance's {@link TextAttributes#getBackgroundColor()} is used for the whole item background.
   *
   * @return attributes to highlight {@link #getPresentableText()}
   */
  @Nullable
  default TextAttributes getPresentableAttributes() {
    return null;
  }

  @Nullable
  default String getLocationText() {
    return null;
  }

  /**
   * @return attributes to highlight {@link #getLocationText()}
   */
  @Nullable
  default TextAttributes getLocationAttributes() {
    return null;
  }

  @Nullable
  default String getRightText() {
    return null;
  }

  @Nullable
  default Icon getRightIcon() {
    return null;
  }
}
