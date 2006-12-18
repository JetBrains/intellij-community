package com.intellij.openapi.actionSystem.impl.config;

import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Bean;
import com.intellij.util.xmlb.annotations.Property;

@Bean(tagName = ActionManagerImpl.ACTION_ELEMENT_NAME)
public class ActionBean {
  @Attribute(name = ActionManagerImpl.ID_ATTR_NAME)
  public String id;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public KeyboardShortcutBean[] keyboardShortcuts;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public AddToGroupBean[] addToGroups;

  @Attribute(name = ActionManagerImpl.CLASS_ATTR_NAME)
  public String actionClass;

  @Attribute(name = ActionManagerImpl.ICON_ATTR_NAME)
  public String icon;


  @Attribute(name = ActionManagerImpl.POPUP_ATTR_NAME)
  public String isPopup;

  @Attribute(name = ActionManagerImpl.TEXT_ATTR_NAME)
  public String text;

  @Attribute(name = ActionManagerImpl.INTERNAL_ATTR_NAME)
  public boolean internal;

  @Attribute(name = "use-shortcut-of")
  public boolean useShortcutOf;

  @Attribute(name = ActionManagerImpl.KEYMAP_ATTR_NAME)
  public String keymap;
}
