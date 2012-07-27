package com.intellij.find.findUsages;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * User: Andrey.Vokin
 * Date: 7/26/12
 */
public abstract class CustomJavaFindUsagesHandlerProvider {
  public static final ExtensionPointName<CustomJavaFindUsagesHandlerProvider> EP_NAME = ExtensionPointName.create("com.intellij.findUsages.java.customJavaFindUsagesHandlerProvider");

  public abstract CustomJavaFindUsagesHandler getHandler();
}
