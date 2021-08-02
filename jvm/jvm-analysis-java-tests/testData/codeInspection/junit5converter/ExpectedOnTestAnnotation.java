// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.junit.Test;

import static org.junit.Assert.*;

public class Simp<caret>le {
  @Test(expected = Exception.class)
  public void testFirst() throws Exception { }
}
