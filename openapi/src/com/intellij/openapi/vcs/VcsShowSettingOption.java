package com.intellij.openapi.vcs;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jun 24, 2005
 * Time: 9:51:58 PM
 * To change this template use File | Settings | File Templates.
 */
public interface VcsShowSettingOption {
  boolean getValue();

  void setValue(boolean value);
}
