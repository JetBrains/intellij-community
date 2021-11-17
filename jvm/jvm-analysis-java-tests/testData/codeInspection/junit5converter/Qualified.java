// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;

import java.util.*;

public class Qual<caret>ified {
  @Before
  public void setUp() {}
  
  @Test
  public void testMethodCall() throws Exception {
    Assert.assertArrayEquals(new Object[] {}, null);
    Assert.assertArrayEquals("message", new Object[] {}, null);
    Assert.assertEquals("Expected", "actual");
    Assert.assertEquals("message", "Expected", "actual");
    Assert.fail();
    Assert.fail("");
  }

  @Test
  public void testMethodRef() {
    List<Boolean> booleanList = new ArrayList<>();
    booleanList.add(true);
    booleanList.forEach(Assert::assertTrue);
  }
}
