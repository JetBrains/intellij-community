package com.intellij.ide.fileTemplates;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class InternalTemplateBean {
  public static ExtensionPointName<InternalTemplateBean> EP_NAME = ExtensionPointName.create("com.intellij.internalFileTemplate");
  
  @Attribute("name")
  public String name;

  @Attribute("subject")
  public String subject;
}
