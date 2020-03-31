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

import com.intellij.facet.Facet;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Base class for tabs of facet editors
 */
public abstract class FacetEditorTab implements Configurable {
  @NotNull
  @Override
  public abstract JComponent createComponent();

  @Override
  public void apply() throws ConfigurationException {
  }

  public void onTabEntering() {
  }

  public void onTabLeaving() {
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  /**
   * Called after user press "OK" or "Apply" in the Project Settings dialog.
   * @param facet facet
   */
  public void onFacetInitialized(@NotNull Facet facet) {
  }
}
