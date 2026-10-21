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

public class MethodModifierTest extends IncrementalTestCase {
  public MethodModifierTest() {
    super("methodModifiers");
  }

  @Test
  public void testDecConstructorAccess() {
    doTest();
  }

  @Test
  public void testIncAccess() {
    doTest();
  }

  @Test
  public void testChangePrivateToPackagePrivate() {
    doTest().assertFailed();
  }
  
  @Test
  public void testChangePrivateToProtected() {
    doTest().assertFailed();
  }

  @Test
  public void testChangePrivateToPublic() {
    doTest().assertFailed();
  }

  @Test
  public void testChangePackagePrivateToProtected() {
    doTest().assertFailed();
  }

  @Test
  public void testChangePackagePrivateToPublic() {
    doTest().assertFailed();
  }

  @Test
  public void testChangeProtectedToPublic() {
    doTest().assertFailed();
  }

  @Test
  public void testSetAbstract() {
    doTest();
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
  public void testSetProtected() {
    doTest();
  }

  @Test
  public void testSetProtectedFromPublic() {
    doTest();
  }

  @Test
  public void testUnsetFinal() {
    doTest();
  }

  @Test
  public void testUnsetAbstractForSAMInterface() {
    doTest();
  }

  @Test
  public void testUnsetStatic() {
    doTest();
  }

  @Test
  public void testSetStatic() {
    doTest();
  }
}
