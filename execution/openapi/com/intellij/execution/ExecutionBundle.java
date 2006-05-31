/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.execution;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 29, 2005
 * Time: 11:45:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class ExecutionBundle {
  @NonNls
  protected static final String PATH_TO_BUNDLE = "messages.ExecutionBundle";

  private ExecutionBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(PATH_TO_BUNDLE), key, params);
  }

}
