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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * Provides component to edit several facets simultaneously. Changes in this component must be porpogated to original facet editors.
 * Use {@link com.intellij.facet.ui.MultipleFacetEditorHelper} to bind controls in editor to corresponding controls in facet editors. 
 *
 * @author nik
 */
public abstract class MultipleFacetSettingsEditor {

  public abstract JComponent createComponent();

  public void disposeUIResources() {
  }

  @Nullable @NonNls
  public String getHelpTopic() {
    return null;
  }
}
