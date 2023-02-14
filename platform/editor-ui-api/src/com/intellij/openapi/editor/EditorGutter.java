// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Provides services for drawing custom text annotations in the editor gutter.
 * Such annotations are used, for example, by the "Annotate" feature of version
 * control integrations.
 * <br>
 * It's also possible to control the display of line numbers in the gutter.
 *
 * @author lesya
 * @see Editor#getGutter()
 */
public interface EditorGutter {
  DataKey<EditorGutter> KEY = DataKey.create("EditorGutter");

  /**
   * Adds a provider for drawing custom text annotations in the editor gutter.
   *
   * @param provider the provider instance.
   */
  void registerTextAnnotation(@NotNull TextAnnotationGutterProvider provider);

  /**
   * Adds a provider for drawing custom text annotations in the editor gutter, with the
   * possibility to execute an action when the annotation is clicked.
   *
   * @param provider the provider instance.
   * @param action the action to execute when the annotation is clicked.
   */
  void registerTextAnnotation(@NotNull TextAnnotationGutterProvider provider, @NotNull EditorGutterAction action);

  boolean isAnnotationsShown();

  @NotNull
  List<TextAnnotationGutterProvider> getTextAnnotations();

  /**
   * Removes all text annotations from the gutter.
   */
  void closeAllAnnotations();

  void closeTextAnnotations(@NotNull Collection<? extends TextAnnotationGutterProvider> annotations);

  /**
   * Changes how line numbers are displayed in the gutter. Disables showing additional line numbers.
   *
   * @see #setLineNumberConverter(LineNumberConverter, LineNumberConverter)
   */
  default void setLineNumberConverter(@NotNull LineNumberConverter converter) {
    setLineNumberConverter(converter, null);
  }

  /**
   * Changes how line numbers are displayed in the gutter.
   *
   * @param primaryConverter converter for primary line number shown in gutter
   * @param additionalConverter if not {@code null}, defines an additional column of numbers to be displayed in the gutter
   */
  void setLineNumberConverter(@NotNull LineNumberConverter primaryConverter, @Nullable LineNumberConverter additionalConverter);
}
