// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle.javadoc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Method comment
 *
 * @author Dmitry Skavish
 */
public class JDMethodComment extends JDParamListOwnerComment {
  private String myReturnTag;
  private List<TagDescription> myThrowsList;

  public JDMethodComment(@NotNull CommentFormatter formatter) {
    super(formatter);
  }

  @Override
  protected void generateSpecial(@NotNull String prefix, @NotNull StringBuilder sb) {
    super.generateSpecial(prefix, sb);

    if (myReturnTag != null) {
      if (myFormatter.getSettings().JD_KEEP_EMPTY_RETURN || !myReturnTag.trim().isEmpty()) {
        JDTag tag = JDTag.RETURN;
        sb.append(myFormatter.getParser().formatJDTagDescription(myReturnTag,
                                                                 prefix + tag.getWithEndWhitespace(),
                                                                 prefix + javadocContinuationIndent()));

        if (myFormatter.getSettings().JD_ADD_BLANK_AFTER_RETURN) {
          sb.append(prefix);
          sb.append('\n');
        }
      }
    }

    if (myThrowsList != null) {
      JDTag tag = myFormatter.getSettings().JD_USE_THROWS_NOT_EXCEPTION ? JDTag.THROWS : JDTag.EXCEPTION;
      generateList(prefix, sb, myThrowsList, tag.getWithEndWhitespace(),
                   myFormatter.getSettings().JD_ALIGN_EXCEPTION_COMMENTS,
                   myFormatter.getSettings().JD_KEEP_EMPTY_EXCEPTION,
                   myFormatter.getSettings().JD_PARAM_DESCRIPTION_ON_NEW_LINE
      );
    }
  }

  public void setReturnTag(@NotNull String returnTag) {
    this.myReturnTag = returnTag;
  }

  public void addThrow(@NotNull String className, @Nullable String description) {
    if (myThrowsList == null) {
      myThrowsList = new ArrayList<>();
    }
    myThrowsList.add(new TagDescription(className, description));
  }
}
