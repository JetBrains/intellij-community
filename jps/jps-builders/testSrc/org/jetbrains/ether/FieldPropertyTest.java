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

import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author: db
 */
public class FieldPropertyTest extends IncrementalTestCase {
  public FieldPropertyTest() {
    super("fieldProperties");
  }

  public void testConstantChain() {
    doTest();
  }

  public void testConstantChain1() {
    doTest();
  }

  public void testConstantChain2() {
    doTest();
  }

  public void testConstantChainMultiModule() {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModule moduleC = addModule("moduleC", "moduleC/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    JpsModuleRootModificationUtil.addDependency(moduleC, moduleB);
    doTestBuild(1).assertSuccessful();
  }

  public void testConstantRemove() {
    doTest();
  }

  public void testConstantRemove1() {
    doTest();
  }

  public void testDoubleConstantChange() {
    doTest();
  }

  public void testFloatConstantChange() {
    doTest();
  }

  public void testInnerConstantChange() {
    doTest();
  }

  public void testIntConstantChange() {
    doTest();
  }

  public void testIntNonStaticConstantChange() {
    doTest();
  }

  public void testLongConstantChange() {
    doTest();
  }

  public void testNonCompileTimeConstant() {
    doTest();
  }

  public void testStringConstantChange() {
    doTest();
  }

  public void testStringConstantLessAccessible() {
    doTest();
  }

  public void testTypeChange() {
    doTest();
  }

  public void testTypeChange1() {
    doTest();
  }

  public void testTypeChange2() {
    doTest();
  }

  public void testNonIncremental1() {
    doTest();
  }

  public void testNonIncremental2() {
    doTest();
  }
  //public void testNonIncremental3() throws Exception {
  //    doTest();
  //  }

  public void testNonIncremental4() {
    doTest();
  }

  public void testMutualConstants() {
    doTest();
  }
}
