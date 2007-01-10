package com.intellij.openapi.actionSystem.impl.config;

import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("reference")
public class ActionReferenceBean {
  @Attribute(ActionManagerImpl.REF_ATTR_NAME)
  public String ref;
}
