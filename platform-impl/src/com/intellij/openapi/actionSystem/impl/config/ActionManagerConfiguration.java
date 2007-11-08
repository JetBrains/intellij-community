package com.intellij.openapi.actionSystem.impl.config;

import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

public class ActionManagerConfiguration {
  @Tag(ActionManagerImpl.ACTIONS_ELEMENT_NAME)
  @AbstractCollection(
    surroundWithTag = false,
    elementTypes = {ActionBean.class, ActionGroupBean.class, ActionReferenceBean.class})
  public Object[] actions;
}
