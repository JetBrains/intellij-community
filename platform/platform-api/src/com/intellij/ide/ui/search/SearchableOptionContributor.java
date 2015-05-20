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
package com.intellij.ide.ui.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * An extension allowing plugins to provide the data at runtime for the setting search to work on.
 * 
 * @author peter
 */
public abstract class SearchableOptionContributor {
  public static final ExtensionPointName<SearchableOptionContributor> EP_NAME = ExtensionPointName.create("com.intellij.search.optionContributor");
  
  public abstract void processOptions(@NotNull SearchableOptionProcessor processor);
  
}
