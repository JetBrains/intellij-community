/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
