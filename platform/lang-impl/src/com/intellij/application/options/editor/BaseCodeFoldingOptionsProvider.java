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

package com.intellij.application.options.editor;

import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.codeInsight.folding.CodeFoldingSettings;

/**
 * @author yole
 */
public class BaseCodeFoldingOptionsProvider extends BeanConfigurable<CodeFoldingSettings> implements CodeFoldingOptionsProvider {
  public BaseCodeFoldingOptionsProvider() {
    super(CodeFoldingSettings.getInstance());
    checkBox("COLLAPSE_FILE_HEADER", ApplicationBundle.message("checkbox.collapse.file.header"));
    checkBox("COLLAPSE_IMPORTS", ApplicationBundle.message("checkbox.collapse.title.imports"));
    checkBox("COLLAPSE_DOC_COMMENTS", ApplicationBundle.message("checkbox.collapse.javadoc.comments"));
    checkBox("COLLAPSE_METHODS", ApplicationBundle.message("checkbox.collapse.method.bodies"));
  }
}
