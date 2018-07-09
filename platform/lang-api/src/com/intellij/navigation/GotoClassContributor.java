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

package com.intellij.navigation;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface GotoClassContributor extends ChooseByNameContributor {
  @Nullable
  String getQualifiedName(NavigationItem item);

  @Nullable
  String getQualifiedNameSeparator();

  /**
   * Override this method to change texts in 'Go to Class' popup and presentation of 'Navigate | Class' action.
   * @return collective name of items provided by this contributor
   * @see #getElementLanguage()
   */
  @NotNull
  default String getElementKind() {
    return IdeBundle.message("go.to.class.kind.text");
  }

  /**
   * If the language returned by this method is one of {@link IdeLanguageCustomization#getPrimaryIdeLanguages() the primary IDE languages} the result of
   * {@link #getElementKind()} will be used to name `Navigate | Class' action and in 'Go to Class' popup.
   * @return the language to which items returned by this contributor belong
   */
  @Nullable
  default Language getElementLanguage() {
    return null;
  }
}
