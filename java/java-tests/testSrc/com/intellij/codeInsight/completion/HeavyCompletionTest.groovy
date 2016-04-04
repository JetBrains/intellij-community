/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.ui.JBColor
import org.jetbrains.annotations.NotNull
/**
 * @author peter
 */
public class HeavyCompletionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testPackagePrefix() throws Throwable {
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    ApplicationManager.application.runWriteAction {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      model.getContentEntries()[0].getSourceFolders()[0].setPackagePrefix("foo.bar.goo");
      model.commit();
    }

    myFixture.completeBasic();
    myFixture.checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo").isValid());
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo.bar").isValid());
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo.bar.goo").isValid());
  }

  public void testPreferTestCases() throws Throwable {
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    ApplicationManager.application.runWriteAction {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      ContentEntry contentEntry = model.getContentEntries()[0];
      SourceFolder sourceFolder = contentEntry.getSourceFolders()[0];
      VirtualFile file = sourceFolder.getFile();
      contentEntry.removeSourceFolder(sourceFolder);
      contentEntry.addSourceFolder(file, true);
      model.commit();
    }

    myFixture.addClass("package foo; public class SomeTestCase {}");
    myFixture.addClass("package bar; public class SomeTestec {}");
    myFixture.addClass("package goo; public class SomeAnchor {}");

    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "SomeTestCase", "SomeAnchor", "SomeTestec");
  }

  public void testAllClassesWhenNothingIsFound() throws Throwable {
    myFixture.addClass("package foo.bar; public class AxBxCxDxEx {}");

    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testAllClassesOnSecondBasicCompletion() throws Throwable {
    myFixture.addClass("package foo.bar; public class AxBxCxDxEx {}");

    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
    LookupElement[] myItems = lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
    assertEquals(2, myItems.length);
    assertEquals("AxBxCxDxEx", myItems[1].getLookupString());
    assertEquals("AyByCyDyEy", myItems[0].getLookupString());
  }
  
  static class CacheVerifyingContributor extends CompletionContributor {
    @Override
    void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
      PsiClass psiClass = PsiTreeUtil.getParentOfType(parameters.position, PsiClass)
      for (ci in OverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, true)) {
        assert ci.element.valid
      }
      for (ci in OverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, false)) {
        assert ci.element.valid
      }
    }
  }

  public void testMapsInvalidation() throws Exception {
    JavaAutoPopupTest.registerCompletionContributor(CacheVerifyingContributor, testRootDisposable, LoadingOrder.FIRST)
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertInstanceOf(myFixture.getFile().getVirtualFile().getFileSystem(), LocalFileSystem.class); // otherwise the completion copy won't be preserved which is critical here
    myFixture.completeBasic();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "getAaa", "getBbb");
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getCaretModel().getOffset() + 2);
    assert myFixture.completeBasic() == null
  }

  public void testQualifyInaccessibleClassName() throws Exception {
    PsiTestUtil.addModule(getProject(), StdModuleTypes.JAVA, "second", myFixture.getTempDirFixture().findOrCreateDir("second"));
    myFixture.addFileToProject("second/foo/bar/AxBxCxDxEx.java", "package foo.bar; class AxBxCxDxEx {}");

    myFixture.configureByText("a.java", "class Main { ABCDE<caret> }");
    myFixture.complete(CompletionType.BASIC, 3);
    myFixture.checkResult("class Main { foo.bar.AxBxCxDxEx<caret> }");
  }

  public void testPreferOwnMethods() {
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/../../../lib")
    def nanoJar = lib.children.find { it.name.startsWith("nanoxml") }

    PsiTestUtil.addLibrary(myModule, 'nano1', lib.path, ["/$nanoJar.name!/"] as String[], [] as String[])

    assert JavaPsiFacade.getInstance(project).findClass('net.n3.nanoxml.StdXMLParser', GlobalSearchScope.allScope(project))

    myFixture.configureByText "a.java", """
public class Test {
  void method(net.n3.nanoxml.StdXMLParser f) {
    f.<caret>
  }
}
"""
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'getBuilder'
  }

  public void testNoJavaStructureModificationOnSecondInvocation() {
    myFixture.configureByText 'a.java', 'class Foo { Xxxxx<caret> }'
    def oldCount = PsiManager.getInstance(project).modificationTracker.javaStructureModificationCount
    assert !myFixture.completeBasic()
    assert !myFixture.completeBasic()
    assert oldCount == PsiManager.getInstance(project).modificationTracker.javaStructureModificationCount
  }

  public void testNoJavaStructureModificationOnSecondInvocationAfterTyping() {
    myFixture.configureByText 'a.java', 'class Foo { Xxxxx<caret> }'

    def tracker = PsiManager.getInstance(project).modificationTracker
    def oldCount = tracker.javaStructureModificationCount
    assert !myFixture.completeBasic()
    assert oldCount == tracker.javaStructureModificationCount

    myFixture.type 'x'
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assert oldCount != tracker.javaStructureModificationCount
    oldCount = tracker.javaStructureModificationCount
    
    assert !myFixture.completeBasic()
    assert !myFixture.completeBasic()
    assert oldCount == tracker.javaStructureModificationCount
  }

  public void testForbiddenApiVariants() {
    IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_1_4)
    myFixture.addClass("""\
package java.nio.channels;
public class SocketChannel {
  public SocketChannel shutdownInput() {}
  public boolean isConnected();
}""")
    myFixture.addClass("package java.nio.channels; public class AsynchronousServerSocketChannel { }")

    myFixture.configureByText 'a.java', 'class Foo {{ new SocketChanne<caret>x }}'
    myFixture.completeBasic()
    def p = LookupElementPresentation.renderElement(myFixture.lookup.items[0])
    assert p.itemText == 'SocketChannel'
    assert p.itemTextForeground == JBColor.foreground()

    p = LookupElementPresentation.renderElement(myFixture.lookup.items.find { it.lookupString == 'AsynchronousServerSocketChannel' })
    assert p.itemTextForeground == JBColor.RED

    myFixture.type('\n.s')
    myFixture.completeBasic()
    p = LookupElementPresentation.renderElement(myFixture.lookup.items[0])
    assert p.itemText == 'shutdownInput'
    assert p.itemTextForeground == JBColor.RED

    p = LookupElementPresentation.renderElement(myFixture.lookup.items.find { it.lookupString == 'isConnected' })
    assert p.itemTextForeground == JBColor.foreground()
  }

  public void "test seemingly scrambled subclass"() {
    PsiTestUtil.addLibrary(myModule, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/normal/seemsScrambled.jar")
    myFixture.configureByText 'a.java', '''import test.Books;

class Foo {{ Books.Test.v<caret> }}
'''
    myFixture.completeBasic()
    myFixture.checkResult '''import test.Books;

class Foo {{ Books.Test.v1<caret> }}
'''

  }

  public void "test different jdks in different modules"() {
    (StatisticsManager.instance as StatisticsManagerImpl).enableStatistics(testRootDisposable)

    def anotherModule = PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'another', myFixture.tempDirFixture.findOrCreateDir('another'))
    ModuleRootModificationUtil.setModuleSdk(anotherModule, IdeaTestUtil.mockJdk17)
    ModuleRootModificationUtil.setModuleSdk(myFixture.module, IdeaTestUtil.mockJdk14)
    ModuleRootModificationUtil.addDependency(myFixture.module, anotherModule)

    myFixture.addFileToProject 'another/Decl.java', '''public class Decl {
public static void method(Runnable r) {}
}
'''
    myFixture.configureByText 'a.java', 'class Usage {{ Decl.method(new <caret>); }}'
    myFixture.complete(CompletionType.SMART)
    myFixture.assertPreferredCompletionItems 0, 'Runnable', 'Thread'
    myFixture.type('\n')

    myFixture.configureByText 'b.java', 'class Usage {{ Decl.method(new <caret>); }}'
    myFixture.complete(CompletionType.SMART)
    myFixture.assertPreferredCompletionItems 0, 'Runnable', 'Thread'
    myFixture.type('\n')
  }

}
