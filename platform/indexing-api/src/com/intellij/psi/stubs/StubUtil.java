// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

public final class StubUtil {
  private StubUtil() {
  }

  static String brokenStubFormat(ObjectStubSerializer root) {
    return "Broken stub format, most likely version of " + root + " was not updated after serialization changes\n";
  }
}
