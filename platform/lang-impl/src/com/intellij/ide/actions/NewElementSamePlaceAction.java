package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;

public class NewElementSamePlaceAction extends NewElementAction {
  @Override
  protected String getPopupTitle() {
    return IdeBundle.message("title.popup.new.element.same.place");
  }
}