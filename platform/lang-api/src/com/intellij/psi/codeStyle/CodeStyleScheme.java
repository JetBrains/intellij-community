/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;

public interface CodeStyleScheme extends Scheme {
  String DEFAULT_SCHEME_NAME = "Default";
  String PROJECT_SCHEME_NAME = "Project";

  String CODE_STYLE_TAG_NAME = "code_scheme";
  String CODE_STYLE_NAME_ATTR = "name";

  @Override
  @NotNull
  String getName();

  boolean isDefault();

  @NotNull
  CodeStyleSettings getCodeStyleSettings();
}
