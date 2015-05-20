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

import com.intellij.openapi.util.SystemInfo;

/**
 * @author: db
 * Date: 05.10.11
 */
public class MethodPropertyTest extends IncrementalTestCase {
  public MethodPropertyTest() throws Exception {
    super("methodProperties");
  }

  public void testAddThrows() throws Exception {
    doTest();
  }

  public void testChangeReturnType() throws Exception {
    doTest();
  }

  public void testChangeMethodRefReturnType() throws Exception {
    if (SystemInfo.isJavaVersionAtLeast("1.8")) {
      doTest();
    }
    else {
      System.err.println("Skipping test " + getTestName(true) + ": java version 8 or higher required to run it");
    }
  }

  public void testChangeLambdaTargetReturnType() throws Exception {
    if (SystemInfo.isJavaVersionAtLeast("1.8")) {
      doTest();
    }
    else {
      System.err.println("Skipping test " + getTestName(true) + ": java version 8 or higher required to run it");
    }
  }

  public void testChangeSAMMethodSignature() throws Exception {
    if (SystemInfo.isJavaVersionAtLeast("1.8")) {
      doTest();
    }
    else {
      System.err.println("Skipping test " + getTestName(true) + ": java version 8 or higher required to run it");
    }
  }

  public void testChangeLambdaSAMMethodSignature() throws Exception {
    if (SystemInfo.isJavaVersionAtLeast("1.8")) {
      doTest();
    }
    else {
      System.err.println("Skipping test " + getTestName(true) + ": java version 8 or higher required to run it");
    }
  }

  public void testChangeReturnType1() throws Exception {
    doTest();
  }

  public void testChangeSignature() throws Exception {
    doTest();
  }

  public void testChangeSignature1() throws Exception {
    doTest();
  }
}
