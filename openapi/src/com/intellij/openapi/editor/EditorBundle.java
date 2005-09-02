/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;
import com.intellij.CommonBundle;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Sep 2, 2005
 * Time: 5:11:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class EditorBundle {
    @NonNls protected static final String PATH_TO_BUNDLE = "com.intellij.openapi.editor.EditorBundle";
      private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

      public static String message(@PropertyKey String key, Object... params) {
        return CommonBundle.message(ourResourceBundle, key, params);
      }

}
