package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author ven
 */
public class CreateFieldFromUsageTest extends LightQuickFixTestCase{

  public void testAnonymousClass() throws Exception { doSingleTest(); }
  public void testExpectedTypes() throws Exception { doSingleTest(); }
  public void testInterface() throws Exception { doSingleTest(); }
  public void testMultipleTypes() throws Exception { doSingleTest(); }
  public void testMultipleTypes2() throws Exception { doSingleTest(); }
  public void testParametericMethod() throws Exception { doSingleTest(); }
  public void testQualifyInner() throws Exception { doSingleTest(); }
  public void testTypeArgsFormatted() throws Exception { doSingleTest(); }

  public void testSortByRelevance() throws Exception {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Exception {
        VirtualFile foo = getSourceRoot().createChildDirectory(this, "foo").createChildData(this, "Foo.java");
        VfsUtil.saveText(foo, "package foo; public class Foo { public void put(Object key, Object value) {} }");
      }
    }.execute();

    doSingleTest();
  }

  protected void doSingleTest() {
    doSingleTest(getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createFieldFromUsage";
  }

}
