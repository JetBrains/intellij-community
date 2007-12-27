/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.JDOMExternalizable;

public abstract class ModuleExtension<T extends ModuleExtension> implements JDOMExternalizable{
  public static final ExtensionPointName<ModuleExtension> EP_NAME = ExtensionPointName.create("com.intellij.moduleExtension");
}