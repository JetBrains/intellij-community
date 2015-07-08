/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author yole
 */
public class ClassUtilTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/psi/classUtil/";
  }

  public void testFindPsiClassByJvmName() {
    myFixture.configureByFile("ManyClasses.java");

    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1$1"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1FooLocal"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1FooLocal$1"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Child"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Child$"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Ma$ked"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Ma$ked$Ne$ted"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Edge"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Edge$"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Edge$$$tu_pid_ne$s"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local$Sub"));

    PsiClass local = ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local$");
    assertNotNull(local);
    assertEquals("Local$", local.getName());

    PsiClass sub = ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local$$Sub");
    assertNotNull(sub);
    assertEquals("Local$", ((PsiClass)sub.getParent()).getName());

    PsiClass fooLocal2 = ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$2FooLocal");
    assertNotNull(fooLocal2);
    assertEquals("Runnable", fooLocal2.getImplementsListTypes()[0].getClassName());
  }
}
