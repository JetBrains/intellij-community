/*
 * User: anna
 * Date: 19-Aug-2008
 */
package com.intellij.codeInsight.generation;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public interface OverrideImplementsAnnotationsHandler {
  ExtensionPointName<OverrideImplementsAnnotationsHandler> EP_NAME = ExtensionPointName.create("com.intellij.overrideImplementsAnnotationsHandler");

  String[] getAnnotations();
  @NotNull
  String [] annotationsToRemove(@NotNull String fqName);
}