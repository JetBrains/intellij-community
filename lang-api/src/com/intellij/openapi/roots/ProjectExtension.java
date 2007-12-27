/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.JDOMExternalizable;

public abstract class ProjectExtension implements JDOMExternalizable{
  public static final ExtensionPointName<ProjectExtension> EP_NAME = ExtensionPointName.create("com.intellij.projectExtension");
}