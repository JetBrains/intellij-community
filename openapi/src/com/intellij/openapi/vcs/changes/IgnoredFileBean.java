/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.12.2006
 * Time: 15:24:28
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class IgnoredFileBean {
  private String myPath;
  private String myMask;
  private Pattern myPattern;

  @Nullable
  public String getPath() {
    return myPath;
  }

  public void setPath(final String path) {
    myPath = path;
  }

  @Nullable
  public String getMask() {
    return myMask;
  }

  public void setMask(final String mask) {
    myMask = mask;
    if (mask == null) {
      myPattern = null;
    }
    else {
      myPattern = PatternUtil.fromMask(mask);
    }
  }

  public Pattern getPattern() {
    return myPattern;
  }
}
