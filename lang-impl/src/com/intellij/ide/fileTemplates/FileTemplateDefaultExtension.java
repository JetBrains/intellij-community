package com.intellij.ide.fileTemplates;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public class FileTemplateDefaultExtension {
  public static ExtensionPointName<FileTemplateDefaultExtension> EP_NAME = ExtensionPointName.create("com.intellij.fileTemplateDefaultExtension"); 

  @Attribute("value")
  public String value;
}
