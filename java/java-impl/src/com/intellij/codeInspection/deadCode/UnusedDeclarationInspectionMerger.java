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
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class UnusedDeclarationInspectionMerger extends InspectionElementsMergerBase {
  private static final String UNUSED_SYMBOL = "UNUSED_SYMBOL";
  private static final String UNUSED_DECLARATION = "UnusedDeclaration";

  @NotNull
  @Override
  public String getMergedToolName() {
    return UnusedDeclarationInspectionBase.SHORT_NAME;
  }

  @NotNull
  @Override
  public String[] getSourceToolNames() {
    return new String[] {UNUSED_SYMBOL, UNUSED_DECLARATION};
  }

  @Override
  protected Element writeOldSettings(String sourceToolName) throws WriteExternalException {
    Element sourceElement = super.writeOldSettings(sourceToolName);
    if (UNUSED_SYMBOL.equals(sourceToolName)) {
      new UnusedSymbolLocalInspection().writeSettings(sourceElement);
    }
    else if (UNUSED_DECLARATION.equals(sourceToolName)) {
      new UnusedDeclarationInspection().writeUnusedDeclarationSettings(sourceElement);
    }
    return sourceElement;
  }
}
