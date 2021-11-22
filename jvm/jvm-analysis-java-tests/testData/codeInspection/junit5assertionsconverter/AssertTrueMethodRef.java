// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class Test1 {
  @Test
  public void testFirst() throws Exception {
    List<Boolean> booleanList = new ArrayList<>();
    booleanList.forEach(Assert::assert<caret>True);
  }
}
