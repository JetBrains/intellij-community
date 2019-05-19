// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;

public abstract class PsiFormatUtilBase {
  public static final int SHOW_NAME = 0x0001; // variable, method, class
  public static final int SHOW_TYPE = 0x0002; // variable, method
  public static final int TYPE_AFTER = 0x0004; // variable, method
  public static final int SHOW_MODIFIERS = 0x0008; // variable, method, class
  public static final int MODIFIERS_AFTER = 0x0010; // variable, method, class
  public static final int SHOW_REDUNDANT_MODIFIERS = 0x0020; // variable, method, class, modifier list
  public static final int SHOW_PACKAGE_LOCAL = 0x0040; // variable, method, class, modifier list
  public static final int SHOW_INITIALIZER = 0x0080; // variable
  public static final int SHOW_PARAMETERS = 0x0100; // method
  public static final int SHOW_THROWS = 0x0200; // method
  public static final int SHOW_EXTENDS_IMPLEMENTS = 0x0400; // class
  public static final int SHOW_FQ_NAME = 0x0800; // class, field, method
  public static final int SHOW_CONTAINING_CLASS = 0x1000; // field, method
  public static final int SHOW_FQ_CLASS_NAMES = 0x2000; // variable, method, class
  public static final int JAVADOC_MODIFIERS_ONLY = 0x4000; // field, method, class
  public static final int SHOW_ANONYMOUS_CLASS_VERBOSE = 0x8000; // class
  public static final int SHOW_RAW_TYPE = 0x10000; //type
  public static final int SHOW_RAW_NON_TOP_TYPE = 0x20000;
  public static final int USE_INTERNAL_CANONICAL_TEXT = 0x40000; // variable/method/parameter types
  public static final int MAX_PARAMS_TO_SHOW = 7;

  protected static void appendSpaceIfNeeded(StringBuilder buffer) {
    if (buffer.length() != 0 && !StringUtil.endsWithChar(buffer, ' ')) {
      buffer.append(' ');
    }
  }
}