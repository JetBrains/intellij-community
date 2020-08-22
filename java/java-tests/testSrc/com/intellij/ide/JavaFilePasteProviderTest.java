// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightPlatformTestCase;

public class JavaFilePasteProviderTest extends LightPlatformTestCase {
  public void testDetectClassName() {
    Project project = getProject();
    assertNull(JavaFilePasteProvider.detectClassName(project, "random text pasted"));
    assertEquals("X", JavaFilePasteProvider.detectClassName(project, "class X {}"));
    assertEquals("Y", JavaFilePasteProvider.detectClassName(project, "class X {} public class Y{}"));
    assertEquals("R", JavaFilePasteProvider.detectClassName(project, "record R() {}"));
  }
}
