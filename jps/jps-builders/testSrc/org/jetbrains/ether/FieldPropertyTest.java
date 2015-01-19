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
 * Date: 23.09.11
 */
public class FieldPropertyTest extends IncrementalTestCase {
  public FieldPropertyTest() throws Exception {
    super("fieldProperties");
  }

  public void testConstantChain() throws Exception {
    doTest();
  }

  public void testConstantChain1() throws Exception {
    doTest();
  }

  public void testConstantChain2() throws Exception {
    doTest();
  }

  public void testConstantChainMultiModule() throws Exception {
    JpsModule moduleA = addModule("moduleA", "moduleA/src");
    JpsModule moduleB = addModule("moduleB", "moduleB/src");
    JpsModule moduleC = addModule("moduleC", "moduleC/src");
    JpsModuleRootModificationUtil.addDependency(moduleB, moduleA);
    JpsModuleRootModificationUtil.addDependency(moduleC, moduleB);
    doTestBuild(1).assertSuccessful();
  }

  public void testConstantRemove() throws Exception {
    doTest();
  }

  public void testConstantRemove1() throws Exception {
    doTest();
  }

  public void testDoubleConstantChange() throws Exception {
    doTest();
  }

  public void testFloatConstantChange() throws Exception {
    doTest();
  }

  public void testInnerConstantChange() throws Exception {
    doTest();
  }

  public void testIntConstantChange() throws Exception {
    doTest();
  }

  public void testIntNonStaticConstantChange() throws Exception {
    doTest();
  }

  public void testLongConstantChange() throws Exception {
    doTest();
  }

  public void testNonCompileTimeConstant() throws Exception {
    doTest();
  }

  public void testStringConstantChange() throws Exception {
    doTest();
  }

  public void testStringConstantLessAccessible() throws Exception {
    doTest();
  }

  public void testTypeChange() throws Exception {
    doTest();
  }

  public void testTypeChange1() throws Exception {
    doTest();
  }

  public void testTypeChange2() throws Exception {
    doTest();
  }

  public void testNonIncremental1() throws Exception {
    doTest();
  }

  public void testNonIncremental2() throws Exception {
    doTest();
  }
  //public void testNonIncremental3() throws Exception {
  //    doTest();
  //  }

  public void testNonIncremental4() throws Exception {
    doTest();
  }

  public void testMutualConstants() throws Exception {
    doTest();
  }
}
