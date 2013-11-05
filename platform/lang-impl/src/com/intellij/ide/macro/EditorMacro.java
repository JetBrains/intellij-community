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
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class EditorMacro extends Macro {
  private final String myName;
  private final String myDescription;

  public EditorMacro(String name, String description) {
    myName = name;
    myDescription = description;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public final String expand(DataContext dataContext) throws ExecutionCancelledException {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    if (ToolWindowManager.getInstance(project).isEditorComponentActive()) {
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      if (editor != null){
        return expand(editor);
      }
    }
    return null;
  }

  @Nullable
  protected abstract String expand(Editor editor);
}
