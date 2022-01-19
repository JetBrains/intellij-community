// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;

import java.util.*;

public class Qualified {
  @BeforeEach
  public void setUp() {}
  
  @Test
  public void testMethodCall() throws Exception {
    Assertions.assertArrayEquals(new Object[] {}, null);
    Assertions.assertArrayEquals(new Object[] {}, null, "message");
    Assertions.assertEquals("Expected", "actual");
    Assertions.assertEquals("Expected", "actual", "message");
    Assertions.fail();
    Assertions.fail("");
  }

  @Test
  public void testMethodRef() {
    List<Boolean> booleanList = new ArrayList<>();
    booleanList.add(true);
    booleanList.forEach(Assertions::assertTrue);
  }
}
