// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Assert;
import java.util.*;

public class UnQual<caret>ified {
  @Test
  public void testMethodCall() throws Exception {
    assertArrayEquals(new Object[] {}, null);
    assertArrayEquals("message", new Object[] {}, null);
    assertEquals("Expected", "actual");
    assertEquals("message", "Expected", "actual");
    fail();
    fail("");
  }

  @Test
  public void testMethodRef() {
    List<Boolean> booleanList = new ArrayList<>();
    booleanList.add(true);
    booleanList.forEach(Assert::assertTrue);
  }
}
