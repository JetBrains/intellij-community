package com.intellij.application.options;

/**
 * @author yole
 */
public class OptionId {
  private OptionId() {
  }

  public static final OptionId RENAME_IN_PLACE = new OptionId(); 
  public static final OptionId COMPLETION_SHOW_STATIC_AFTER_IMPORT = new OptionId(); 
  public static final OptionId COMPLETION_AUTO_POPUP_XML = new OptionId(); 
  public static final OptionId COMPLETION_AUTO_POPUP_DOC_COMMENT = new OptionId();
  public static final OptionId COMPLETION_DOUBLE_PAREN_NO_ARGS = new OptionId(); 
}
