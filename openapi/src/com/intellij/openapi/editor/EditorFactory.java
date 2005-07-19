/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;

public abstract class EditorFactory implements ApplicationComponent {
  public static EditorFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(EditorFactory.class);
  }

  public abstract Document createDocument(CharSequence text);

  public abstract Editor createEditor(Document document);

  public abstract Editor createViewer(Document document);

  public abstract Editor createEditor(Document document, Project project);

  public abstract Editor createViewer(Document document, Project project);

  public abstract void releaseEditor(Editor editor);

  public abstract Editor[] getEditors(Document document, Project project);

  public abstract Editor[] getEditors(Document document);

  public abstract Editor[] getAllEditors();

  public abstract void addEditorFactoryListener(EditorFactoryListener listener);
  public abstract void removeEditorFactoryListener(EditorFactoryListener listener);

  public abstract EditorEventMulticaster getEventMulticaster();

  public abstract Document createDocument(char[] text);

  public abstract void refreshAllEditors();
}
