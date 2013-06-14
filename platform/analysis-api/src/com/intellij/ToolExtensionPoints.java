/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;

public interface ToolExtensionPoints {
  @NonNls String INVALID_PROPERTY_KEY_INSPECTION_TOOL = "com.intellij.invalidPropertyKeyInspectionTool";
  @NonNls String I18N_INSPECTION_TOOL = "com.intellij.i18nInspectionTool";
  @NonNls String JAVA15_INSPECTION_TOOL = "com.intellij.java15InspectionTool";


  @NonNls String INSPECTIONS_GRAPH_ANNOTATOR = "com.intellij.refGraphAnnotator";

  @NonNls String DEAD_CODE_TOOL = "com.intellij.deadCode";

  @NonNls String JAVADOC_LOCAL = "com.intellij.javaDocNotNecessary";

  @NonNls String VISIBLITY_TOOL = "com.intellij.visibility";

  @NonNls String EMPTY_METHOD_TOOL = "com.intellij.canBeEmpty";

}
