/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.JDOMExternalizable;

public abstract class LanguageModuleExtension<T extends LanguageModuleExtension> implements JDOMExternalizable{
  public static final ExtensionPointName<LanguageModuleExtension> EP_NAME = ExtensionPointName.create("com.intellij.moduleExtension");

  public abstract void copy(T source);
  public abstract boolean isModified(T source);
}