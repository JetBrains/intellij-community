/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.ether;

import org.junit.jupiter.api.Test;

public class FieldModifierTest extends IncrementalTestCase {
  public FieldModifierTest() {
    super("fieldModifiers");
  }

  @Test
  public void testSetFinal() {
    doTest();
  }

  @Test
  public void testSetPrivate() {
    doTest();
  }

  @Test
  public void testSetPrivateToConstantField() {
    doTest();
  }

  @Test
  public void testSetProtected() {
    doTest();
  }

  @Test
  public void testSetPackagePrivate() {
    doTest();
  }

  @Test
  public void testSetStatic() {
    doTest();
  }

  @Test
  public void testUnsetStatic() {
    doTest();
  }

  @Test
  public void testUnsetStaticFinal() {
    doTest();
  }

  @Test
  public void testHidePackagePrivateWithPackagePrivate() {
    doTest();
  }

  @Test
  public void testHidePackagePrivateWithProtected() {
    doTest();
  }

  @Test
  public void testHidePackagePrivateWithPublic() {
    doTest();
  }

  @Test
  public void testHideProtectedWithPackagePrivate() {
    doTest();
  }

  @Test
  public void testHideProtectedWithProtected() {
    doTest();
  }

  @Test
  public void testHideProtectedWithPublic() {
    doTest();
  }

  @Test
  public void testHidePublicWithPackagePrivate() {
    doTest();
  }

  @Test
  public void testHidePublicWithProtected() {
    doTest();
  }

  @Test
  public void testHidePublicWithPublic() {
    doTest();
  }
}
