// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.comment;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class ChangeToCStyleLineCommentIntentionTest extends IPPTestCase {

  public void testConvertMultiLineTodo() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("change.to.c.style.comment.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "comment/to_c_style";
  }
}