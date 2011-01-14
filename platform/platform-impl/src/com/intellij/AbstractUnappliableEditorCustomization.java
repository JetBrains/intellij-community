/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.EditorCustomization;
import org.jetbrains.annotations.NotNull;

/**
 * Basic super class for {@link EditorCustomization editor customizations} that don't consider un-applying feature.
 * 
 * @author Denis Zhdanov
 * @since 1/14/11 12:52 PM
 */
public abstract class AbstractUnappliableEditorCustomization implements EditorCustomization {

  @Override
  public void removeCustomization(@NotNull EditorEx editor, @NotNull Feature feature) {
    // Do nothing
  }
}
