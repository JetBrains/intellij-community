// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.containers.ContainerUtil;

public class Normal6CompletionTest extends NormalCompletionTestCase {
  public void testMakeMultipleArgumentsFinalWhenInInner() {
    configure();
    LookupElement item = ContainerUtil.find(getLookup().getItems(), it -> "a, b".equals(it.getLookupString()));
    assertNotNull(item);
    getLookup().setCurrentItem(item);
    type("\n");
    checkResult();
  }

  public void testOverwriteGenericsAfterNew() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testExplicitTypeArgumentsWhenParameterTypesDoNotDependOnTypeParameters() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testClassNameWithGenericsTab2() { doTest("\t"); }

  @NeedsIndex.ForStandardLibrary
  public void testClassNameGenerics() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testDoubleExpectedTypeFactoryMethod() {
    configure();
    assertStringItems("Key", "create", "create");
    assertEquals("Key.<Boolean>create", NormalCompletionTestCase.renderElement(myItems[1]).getItemText());
    assertEquals("Key.create", NormalCompletionTestCase.renderElement(myItems[2]).getItemText());
  }
}
