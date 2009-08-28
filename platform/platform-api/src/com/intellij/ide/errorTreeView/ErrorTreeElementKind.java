/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView;

import com.intellij.util.ui.MessageCategory;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public enum ErrorTreeElementKind {
  INFO ("INFO", IdeBundle.message("errortree.information")),
  ERROR ("ERROR", IdeBundle.message("errortree.error")),
  WARNING ("WARNING", IdeBundle.message("errortree.warning")),
  GENERIC ("GENERIC", "");

  private final String myText;
  private final String myPresentableText;

  private ErrorTreeElementKind(@NonNls String text, String presentableText) {
    myText = text;
    myPresentableText = presentableText;
  }

  public String toString() {
    return myText; // for debug purposes
  }

  public String getPresentableText() {
    return myPresentableText;
  }

  public static ErrorTreeElementKind convertMessageFromCompilerErrorType(int type) {
    switch(type) {
      case MessageCategory.ERROR : return ERROR;
      case MessageCategory.WARNING : return WARNING;
      case MessageCategory.INFORMATION : return INFO;
      case MessageCategory.STATISTICS : return INFO;
      case MessageCategory.SIMPLE : return GENERIC;
      default : return GENERIC;
    }
  }
}
