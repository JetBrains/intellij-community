// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import static org.junit.Assert.*;

import org.junit.jupiter.api.Test;

class Test1 {
  @Test
  public void testFirst() throws Exception {
    assert<caret>ArrayEquals(new Object[] {}, null);
  }
}
