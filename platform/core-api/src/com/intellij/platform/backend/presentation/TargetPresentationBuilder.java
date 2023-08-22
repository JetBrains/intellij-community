// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.presentation;

import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface TargetPresentationBuilder {

  @Contract(value = "-> new", pure = true)
  @NotNull TargetPresentation presentation();

  /**
   * @see TargetPresentation#getBackgroundColor
   * @see com.intellij.openapi.vfs.newvfs.VfsPresentationUtil#getFileBackgroundColor
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder backgroundColor(@Nullable Color color);

  /**
   * @see TargetPresentation#getIcon
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder icon(@Nullable Icon icon);

  /**
   * @see TargetPresentation#getPresentableText
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder presentableText(@Nls @NotNull String text);

  /**
   * @see TargetPresentation#getPresentableTextAttributes
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder presentableTextAttributes(@Nullable TextAttributes attributes);

  /**
   * @see TargetPresentation#getContainerText
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder containerText(@Nls @Nullable String text);

  /**
   * @see TargetPresentation#getContainerText
   * @see TargetPresentation#getContainerTextAttributes
   */
  @Contract(value = "_, _ -> new", pure = true)
  @NotNull TargetPresentationBuilder containerText(@Nls @Nullable String text, @Nullable TextAttributes attributes);

  /**
   * @see TargetPresentation#getContainerTextAttributes
   * @see com.intellij.codeInsight.navigation.UtilKt#fileStatusAttributes
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder containerTextAttributes(@Nullable TextAttributes attributes);

  /**
   * @see TargetPresentation#getLocationText
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder locationText(@Nls @Nullable String text);

  /**
   * @see TargetPresentation#getLocationText
   * @see TargetPresentation#getLocationIcon
   * @see com.intellij.codeInsight.navigation.UtilKt#fileLocation
   */
  @Contract(value = "_, _ -> new", pure = true)
  @NotNull TargetPresentationBuilder locationText(@Nls @Nullable String text, @Nullable Icon icon);
}
