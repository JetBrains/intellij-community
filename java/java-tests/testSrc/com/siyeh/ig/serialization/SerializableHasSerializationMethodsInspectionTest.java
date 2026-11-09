// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class SerializableHasSerializationMethodsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new SerializableHasSerializationMethodsInspection());

  @Test
  public void testExternalizable() {
    //noinspection ExternalizableWithoutPublicNoArgConstructor
    inspectionFixture.get().doTest("class Test implements java.io.Externalizable {" +
           "    @Override" +
           "    public void writeExternal(java.io.ObjectOutput out) {" +
           "    }" +
           "    @Override" +
           "    public void readExternal(java.io.ObjectInput in) {" +
           "    }" +
           "}");
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("class /*Serializable class 'Simple' does not define 'readObject()' or 'writeObject()'*/Simple/**/" +
           " implements java.io.Serializable {}");
  }
}
