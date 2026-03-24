// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.jps.dependency.ElementInterner;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.Usage;

// shared facade for interning functionality
public final class GraphElementInterner {
  private static ElementInterner ourImpl = ElementInterner.IDENTITY; // no interning by default

  public static void setImplementation(ElementInterner impl) {
    ElementInterner old = ourImpl;
    ourImpl = impl;
    if (old != null) {
      old.clear();
    }
  }

  public static String intern(String str) {
    return ourImpl.intern(str);
  }
  
  public static <T extends Usage> T intern(T usage) {
    return ourImpl.intern(usage);
  }

  public static <T extends ReferenceID> T intern(T id) {
    return ourImpl.intern(id);
  }

  public static void clear() {
    ourImpl.clear();
  }
}
