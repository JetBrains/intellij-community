package com.intellij.openapi.project.impl.convertors;

import org.jdom.Element;

@SuppressWarnings({"HardCodedStringLiteral"})
public class Convertor12 {
  private static final String OLD_PROJECT_ROOT_CONTAINER_CLASS = "com.intellij.project.ProjectRootContainer";
  private static final String NEW_PROJECT_ROOT_CONTAINER_CLASS = "com.intellij.projectRoots.ProjectRootContainer";

  public static void execute(Element root) {
    Element rootContComponent = Util.findComponent(root, OLD_PROJECT_ROOT_CONTAINER_CLASS);
    if (rootContComponent != null) {
      rootContComponent.setAttribute("class", NEW_PROJECT_ROOT_CONTAINER_CLASS);
    }
  }
}