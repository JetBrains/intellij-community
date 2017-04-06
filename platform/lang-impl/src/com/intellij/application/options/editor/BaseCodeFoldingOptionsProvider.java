/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.BeanConfigurable;

/**
 * @author yole
 */
public class BaseCodeFoldingOptionsProvider extends BeanConfigurable<CodeFoldingSettings> implements CodeFoldingOptionsProvider {
  public BaseCodeFoldingOptionsProvider() {
    super(CodeFoldingSettings.getInstance());
    CodeFoldingSettings settings = getInstance();
    checkBox(ApplicationBundle.message("checkbox.collapse.file.header"), settings::isCollapseFileHeader, settings::setCollapseFileHeader);
    checkBox(ApplicationBundle.message("checkbox.collapse.title.imports"), settings::isCollapseImports, settings::setCollapseImports);
    checkBox(ApplicationBundle.message("checkbox.collapse.javadoc.comments"), settings::isCollapseDocComments, settings::setCollapseDocComments);
    checkBox(ApplicationBundle.message("checkbox.collapse.method.bodies"), settings::isCollapseMethods, settings::setCollapseMethods);
    checkBox(ApplicationBundle.message("checkbox.collapse.custom.folding.regions"), settings::isCollapseCustomFoldingRegions, settings::setCollapseCustomFoldingRegions);
  }
}
