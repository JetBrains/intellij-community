/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 04.08.2006
 * Time: 17:57:56
 */
package com.intellij.execution.filters;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author dyoma
 */
public class TextConsoleBuilderFactoryImpl extends TextConsoleBuilderFactory implements ApplicationComponent {
  public TextConsoleBuilder createBuilder(final Project project) {
    return new TextConsoleBuilderImpl(project);
  }

  @NotNull
  public String getComponentName() {
    return "TextConsoleBuilderFactory";
  }

  public void initComponent() {}
  public void disposeComponent() {}
}