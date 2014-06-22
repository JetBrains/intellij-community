package ru.compscicenter.edide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.EditorFactory;
import org.jetbrains.annotations.NotNull;

/**
 * author: liana
 * data: 6/20/14.
 */
public class StudyApplicationComponent implements ApplicationComponent{
  private final Application myApp;

  public StudyApplicationComponent(Application app) {
    myApp = app;
  }

  @Override
  public void initComponent() {
    EditorFactory.getInstance().addEditorFactoryListener(new StudyEditorFactoryListener(), myApp);
  }

  @Override
  public void disposeComponent() {

  }

  @NotNull
  @Override
  public String getComponentName() {
    return "Educational plugin";
  }
}
