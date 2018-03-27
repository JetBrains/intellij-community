/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.errorTreeView;

import com.intellij.util.ui.MessageCategory;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public enum ErrorTreeElementKind {
  INFO ("INFO", IdeBundle.message("errortree.information")),
  ERROR ("ERROR", IdeBundle.message("errortree.error")),
  WARNING ("WARNING", IdeBundle.message("errortree.warning")),
  NOTE ("NOTE", IdeBundle.message("errortree.note")),
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

  @NotNull
  public static ErrorTreeElementKind convertMessageFromCompilerErrorType(int type) {
    switch(type) {
      case MessageCategory.ERROR : return ERROR;
      case MessageCategory.WARNING : return WARNING;
      case MessageCategory.INFORMATION : return INFO;
      case MessageCategory.STATISTICS : return INFO;
      case MessageCategory.SIMPLE : return GENERIC;
      case MessageCategory.NOTE : return NOTE;
      default : return GENERIC;
    }
  }
}
