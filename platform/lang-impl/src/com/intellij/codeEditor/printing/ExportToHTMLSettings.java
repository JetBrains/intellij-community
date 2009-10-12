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

package com.intellij.codeEditor.printing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;

@State(
  name = "ExportToHTMLSettings",
  storages = {
    @Storage(
      id ="other",
      file = "$PROJECT_FILE$"
    )}
)
public class ExportToHTMLSettings implements PersistentStateComponent<ExportToHTMLSettings> {
  public boolean PRINT_LINE_NUMBERS;
  public boolean OPEN_IN_BROWSER;
  @NonNls public String OUTPUT_DIRECTORY;

  private int myPrintScope;

  private boolean isIncludeSubpackages = false;

  public static ExportToHTMLSettings getInstance(Project project) {
    return ServiceManager.getService(project, ExportToHTMLSettings.class);
  }

  public int getPrintScope() {
    return myPrintScope;
  }

  public void setPrintScope(int printScope) {
    myPrintScope = printScope;
  }

  public boolean isIncludeSubdirectories() {
    return isIncludeSubpackages;
  }

  public void setIncludeSubpackages(boolean includeSubpackages) {
    isIncludeSubpackages = includeSubpackages;
  }


  public ExportToHTMLSettings getState() {
    return this;
  }

  public void loadState(ExportToHTMLSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
