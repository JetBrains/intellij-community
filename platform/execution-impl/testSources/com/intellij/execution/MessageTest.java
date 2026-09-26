// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import org.junit.Assert;
import org.junit.Test;

public class MessageTest {
  @Test
  public void messageBundleTest() {
    Assert.assertNotEquals(
      "ExecutionBundle.properties was broken",
      "!message.error.happened.0!",
      ExecutionBundle.message("message.error.happened.0", "value")
    );
  }
}
