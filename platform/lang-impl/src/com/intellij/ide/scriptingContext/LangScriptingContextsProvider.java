/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.scriptingContext;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Rustam Vishnyakov
 */
public abstract class LangScriptingContextsProvider {

  public static final ExtensionPointName<LangScriptingContextsProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.langScriptingContextsProvider");

  @NotNull
  public abstract Language getLanguage();

  @NotNull
  public static LangScriptingContextsProvider[] getProviders() {
    return Extensions.getExtensions(EP_NAME);
  }
}
