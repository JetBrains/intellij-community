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
 * It's also possible to control the display of line numbers in gutter.
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
   * Changes the display of line numbers in gutter. Disables showing additional line numbers.
   *
   * @see #setLineNumberConverter(LineNumberConverter, LineNumberConverter)
   */
  default void setLineNumberConverter(@NotNull LineNumberConverter converter) {
    setLineNumberConverter(converter, null);
  }

  /**
   * Changes the display of line numbers in gutter
   *
   * @param primaryConverter converter for primary line number shown in gutter
   * @param additionalConverter if not {@code null}, defines an additional column of numbers to be displayed in gutter
   */
  void setLineNumberConverter(@NotNull LineNumberConverter primaryConverter, @Nullable LineNumberConverter additionalConverter);
}
