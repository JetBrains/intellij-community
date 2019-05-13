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
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import org.jetbrains.annotations.NotNull;

public class DummyEntryPointsEP extends InspectionEP {
  public DummyEntryPointsEP() {
    presentation = DummyEntryPointsPresentation.class.getName();
    displayName = InspectionsBundle.message("inspection.dead.code.entry.points.display.name");
    implementationClass = "";
    shortName = "";
  }

  @NotNull
  @Override
  public InspectionProfileEntry instantiateTool() {
    return new DummyEntryPointsTool();
  }
}
