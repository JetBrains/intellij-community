/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
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
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        final ModifiableRootModel model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
        model.getContentEntries()[0].getSourceFolders()[0].setPackagePrefix("foo.bar.goo");
        model.commit();
      }
    }.execute().throwException();

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
  
  public void testAllClassesOnSecondBasicCompletion() throws Throwable {
    createClass("package foo.bar; public class AxBxCxDxEx {}");

    configureByFileNoCompletion("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(getProject(), getEditor(), 2, false);
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    myItems = lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
    assertEquals(2, myItems.length);
    assertEquals("AxBxCxDxEx", myItems[1].getLookupString());
    assertEquals("AyByCyDyEy", myItems[0].getLookupString());
  }

  public void testMapsInvalidation() throws Exception {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assert myFile.getVirtualFile().getFileSystem() instanceof LocalFileSystem; // otherwise the completion copy won't be preserved which is critical here
    assertStringItems("finalize", "foo");
    myEditor.getCaretModel().moveToOffset(myEditor.getCaretModel().getOffset() + 2);
    complete();
    assertNull(myItems);
  }

}
