// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.packaging;

import com.siyeh.ig.IGInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class PackageWithTooFewClassesInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/packaging/package_with_too_few_classes", new PackageWithTooFewClassesInspection());
  }
}
