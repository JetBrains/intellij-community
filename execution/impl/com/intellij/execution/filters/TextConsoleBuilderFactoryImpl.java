/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 04.08.2006
 * Time: 17:57:56
 */
package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;

/**
 * @author dyoma
 */
public class TextConsoleBuilderFactoryImpl extends TextConsoleBuilderFactory {
  public TextConsoleBuilder createBuilder(final Project project) {
    return new TextConsoleBuilderImpl(project);
  }

}