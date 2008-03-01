package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public interface DefaultLiveTemplatesProvider {
  ExtensionPointName<DefaultLiveTemplatesProvider> EP_NAME = ExtensionPointName.create("com.intellij.defaultLiveTemplatesProvider");

  String[] getDefaultLiveTemplateFiles();
}
