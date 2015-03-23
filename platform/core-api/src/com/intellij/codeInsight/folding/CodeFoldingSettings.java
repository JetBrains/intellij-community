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
package com.intellij.codeInsight.folding;

import com.intellij.openapi.components.ServiceManager;

public class CodeFoldingSettings {
  public boolean COLLAPSE_IMPORTS = true;
  public boolean COLLAPSE_METHODS = false;
  public boolean COLLAPSE_FILE_HEADER = true;
  public boolean COLLAPSE_DOC_COMMENTS = false;
  public boolean COLLAPSE_CUSTOM_FOLDING_REGIONS = false;

  public static CodeFoldingSettings getInstance() {
    return ServiceManager.getService(CodeFoldingSettings.class);
  }
}
