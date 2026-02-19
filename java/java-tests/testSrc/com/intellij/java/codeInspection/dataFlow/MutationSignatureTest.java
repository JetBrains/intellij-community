// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.MutationSignature;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MutationSignatureTest {
  @Test
  public void testParse() {
    MutationSignature sig = MutationSignature.parse("this");
    assertTrue(sig.mutatesThis());
    assertFalse(sig.performsIO());
    assertFalse(sig.mutatesArg(0));
    sig = MutationSignature.parse("param1 , param2");
    assertFalse(sig.mutatesThis());
    assertFalse(sig.performsIO());
    assertTrue(sig.mutatesArg(0));
    assertTrue(sig.mutatesArg(1));
    assertFalse(sig.mutatesArg(2));
    sig = MutationSignature.parse("");
    assertFalse(sig.mutatesThis());
    assertFalse(sig.performsIO());
    assertFalse(sig.mutatesArg(0));
    sig = MutationSignature.parse("this,io");
    assertTrue(sig.mutatesThis());
    assertTrue(sig.performsIO());
    assertFalse(sig.mutatesArg(0));
    assertFalse(sig.mutatesArg(1));
  }
}
