/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.JavaPsiFacade;

/**
 * @author peter
 */
public class HeavyNormalCompletionTest extends CompletionTestCase{

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testPackagePrefix() throws Throwable {
    configureByFileNoCompletion("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    final ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    model.getContentEntries()[0].getSourceFolders()[0].setPackagePrefix("foo.bar.goo");
    model.commit();
    complete();
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
    assertTrue(JavaPsiFacade.getInstance(myProject).findPackage("foo").isValid());
    assertTrue(JavaPsiFacade.getInstance(myProject).findPackage("foo.bar").isValid());
    assertTrue(JavaPsiFacade.getInstance(myProject).findPackage("foo.bar.goo").isValid());
  }

  public void testAllClassesWhenNothingIsFound() throws Throwable {
    createClass("package foo.bar; public class AxBxCxDxEx {}");

    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }
  
  public void testOnlyUppercaseClassesWhenNothingIsFound() throws Throwable {
    createClass("package foo.bar; public class aaaxBxCxDxEx {}");

    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
  }

  public void testAllClassesOnSecondBasicCompletion() throws Throwable {
    createClass("package foo.bar; public class AxBxCxDxEx {}");

    configureByFileNoCompletion("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(getProject(), getEditor(), getFile(), 2);
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    myItems = lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
    assertEquals(2, myItems.length);
    assertEquals("AxBxCxDxEx", myItems[0].getLookupString());
    assertEquals("AyByCyDyEy", myItems[1].getLookupString());
  }

}
