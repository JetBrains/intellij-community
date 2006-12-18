package com.intellij.openapi.actionSystem.impl.config;

import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Bean;

@Bean(tagName = ActionManagerImpl.ADD_TO_GROUP_ELEMENT_NAME)
public class AddToGroupBean {

  @Attribute(name = ActionManagerImpl.ANCHOR_ELEMENT_NAME)
  public String anchor;

  @Attribute(name = ActionManagerImpl.GROUPID_ATTR_NAME)
  public String groupId;

  @Attribute(name = ActionManagerImpl.RELATIVE_TO_ACTION_ATTR_NAME)
  public String relativeToAction;
}
