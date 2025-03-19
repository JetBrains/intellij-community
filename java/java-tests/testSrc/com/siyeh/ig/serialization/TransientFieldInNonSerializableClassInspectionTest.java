// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class TransientFieldInNonSerializableClassInspectionTest extends LightJavaInspectionTestCase {

  public void testNonSerializable() {
    doTest();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new TransientFieldInNonSerializableClassInspection();
  }

}