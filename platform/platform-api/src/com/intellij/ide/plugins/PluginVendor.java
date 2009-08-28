package com.intellij.ide.plugins;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Text;

@Tag("vendor")
public class PluginVendor {
  @Attribute("url")
  public String url;

  @Attribute("email")
  public String email;

  @Attribute("logo")
  public String logo;

  @Text
  public String name;
}
