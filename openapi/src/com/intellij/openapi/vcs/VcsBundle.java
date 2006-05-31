/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.vcs;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 24, 2005
 * Time: 3:50:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class VcsBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "messages.VcsBundle";

  private VcsBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = "messages.VcsBundle") String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(PATH_TO_BUNDLE), key, params);
  }
}
