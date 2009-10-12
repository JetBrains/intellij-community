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
package com.intellij.ide.actions;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;

public class ExportToTextFileToolbarAction extends ExportToTextFileAction {
  private final ExporterToTextFile myExporterToTextFile;

  public ExportToTextFileToolbarAction(ExporterToTextFile exporterToTextFile) {
    myExporterToTextFile = exporterToTextFile;
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPORT_TO_TEXT_FILE));
  }

  protected ExporterToTextFile getExporter(DataContext dataContext) {
    return myExporterToTextFile;
  }
}
