/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.facet.ui;

import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Use {@link com.intellij.facet.ui.FacetEditorsFactory#createMultipleFacetEditorHelper()} to create instance of this class.
 *
 * @author nik
 */
public interface MultipleFacetEditorHelper {

  /**
   * Binds <code>common</code> 3-state checkbox to checkboxes in facet editors in such a way that all changes in it will be propogated to
   * target checkboxes.
   * @param common checkbox
   * @param editors editors
   * @param fun maps a facet editor to checkbox inside one of its tabs
   */
  void bind(@NotNull ThreeStateCheckBox common, @NotNull FacetEditor[] editors, @NotNull NotNullFunction<FacetEditor, JCheckBox> fun);

  void bind(@NotNull JTextField common, @NotNull FacetEditor[] editors, @NotNull NotNullFunction<FacetEditor, JTextField> fun);

  void bind(@NotNull JComboBox common, @NotNull FacetEditor[] editors, @NotNull NotNullFunction<FacetEditor, JComboBox> fun);

  /**
   * Removes all bindings registered by this helper
   */
  void unbind();
}
