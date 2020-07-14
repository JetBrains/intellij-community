// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.ui.JBColor
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

import static com.intellij.java.codeInsight.completion.NormalCompletionTestCase.renderElement
/**
 * @author peter
 */
@CompileStatic
class HeavyCompletionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath()
  }

  void testPackagePrefix() throws Throwable {
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java")
    ApplicationManager.application.runWriteAction {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel()
      model.getContentEntries()[0].getSourceFolders()[0].setPackagePrefix("foo.bar.goo")
      model.commit()
    }

    myFixture.completeBasic()
    myFixture.checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java")
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo").isValid())
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo.bar").isValid())
    assertTrue(JavaPsiFacade.getInstance(getProject()).findPackage("foo.bar.goo").isValid())
  }

  @NeedsIndex.Full
  void testPreferTestCases() throws Throwable {
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java")
    ApplicationManager.application.runWriteAction {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel()
      ContentEntry contentEntry = model.getContentEntries()[0]
      SourceFolder sourceFolder = contentEntry.getSourceFolders()[0]
      VirtualFile file = sourceFolder.getFile()
      contentEntry.removeSourceFolder(sourceFolder)
      contentEntry.addSourceFolder(file, true)
      model.commit()
    }

    myFixture.addClass("package foo; public class SomeTestCase {}")
    myFixture.addClass("package bar; public class SomeTestec {}")
    myFixture.addClass("package goo; public class SomeAnchor {}")

    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems(0, "SomeTestCase", "SomeAnchor", "SomeTestec")
  }

  @NeedsIndex.Full
  void testAllClassesWhenNothingIsFound() throws Throwable {
    myFixture.addClass("package foo.bar; public class AxBxCxDxEx {}")

    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java")
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java")
  }

  @NeedsIndex.Full
  void testAllClassesOnSecondBasicCompletion() throws Throwable {
    myFixture.addClass("package foo.bar; public class AxBxCxDxEx {}")

    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
    assert myFixture.lookupElementStrings == ["AyByCyDyEy", "AxBxCxDxEx"]
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

  void testMapsInvalidation() throws Exception {
    JavaAutoPopupTest.registerCompletionContributor(CacheVerifyingContributor, myFixture.testRootDisposable, LoadingOrder.FIRST)
    myFixture.configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java")
    assertInstanceOf(myFixture.getFile().getVirtualFile().getFileSystem(), LocalFileSystem.class) // otherwise the completion copy won't be preserved which is critical here
    myFixture.completeBasic()
    assertOrderedEquals(myFixture.getLookupElementStrings(), "getAaa", "getBbb")
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getCaretModel().getOffset() + 2)
    assert myFixture.completeBasic() == null
  }

  @NeedsIndex.Full
  void testQualifyInaccessibleClassName() throws Exception {
    PsiTestUtil.addModule(getProject(), StdModuleTypes.JAVA, "second", myFixture.getTempDirFixture().findOrCreateDir("second"))
    myFixture.addFileToProject("second/foo/bar/AxBxCxDxEx.java", "package foo.bar; class AxBxCxDxEx {}")

    myFixture.configureByText("a.java", "class Main { ABCDE<caret> }")
    myFixture.complete(CompletionType.BASIC, 3)
    myFixture.checkResult("class Main { foo.bar.AxBxCxDxEx<caret> }")
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferOwnMethods() {
    def nanoUrls = IntelliJProjectConfiguration.getProjectLibraryClassesRootUrls("NanoXML")
    ModuleRootModificationUtil.addModuleLibrary(module, 'nano1', nanoUrls, [])

    def finalProject = project
    FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, { ->
      assert JavaPsiFacade.getInstance(finalProject).findClass('net.n3.nanoxml.StdXMLParser', GlobalSearchScope.allScope(finalProject))
    })

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

  void testNoJavaStructureModificationOnSecondInvocation() {
    myFixture.configureByText 'a.java', 'class Foo { Xxxxx<caret> }'
    def oldCount = PsiManager.getInstance(project).modificationTracker.modificationCount
    assert !myFixture.completeBasic()
    assert !myFixture.completeBasic()
    assert oldCount == PsiManager.getInstance(project).modificationTracker.modificationCount
  }

  void testNoJavaStructureModificationOnSecondInvocationAfterTyping() {
    myFixture.configureByText 'a.java', 'class Foo { Xxxxx<caret> }'

    def tracker = PsiManager.getInstance(project).modificationTracker
    def oldCount = tracker.modificationCount
    assert !myFixture.completeBasic()
    assert oldCount == tracker.modificationCount

    myFixture.type 'x'
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assert oldCount != tracker.modificationCount
    oldCount = tracker.modificationCount

    assert !myFixture.completeBasic()
    assert !myFixture.completeBasic()
    assert oldCount == tracker.modificationCount
  }

  @NeedsIndex.Full
  void testForbiddenApiVariants() {
    IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_1_4)
    myFixture.addClass("""\
package java.nio.channels;
public class SocketChannel {
  public SocketChannel shutdownInput() {}
  public boolean isConnected();
}""")
    myFixture.addClass("package java.nio.channels; public class AsynchronousServerSocketChannel { }")

    myFixture.configureByText 'a.java', 'class Foo {{ new SocketChanne<caret>x }}'
    myFixture.completeBasic()
    def p = renderElement(myFixture.lookup.items[0])
    assert p.itemText == 'SocketChannel'
    assert p.itemTextForeground == JBColor.foreground()

    p = renderElement(myFixture.lookup.items.find { it.lookupString == 'AsynchronousServerSocketChannel' })
    assert p.itemTextForeground == JBColor.RED

    myFixture.type('\n.s')
    myFixture.completeBasic()
    p = renderElement(myFixture.lookup.items[0])
    assert p.itemText == 'shutdownInput'
    assert p.itemTextForeground == JBColor.RED

    p = renderElement(myFixture.lookup.items.find { it.lookupString == 'isConnected' })
    assert p.itemTextForeground == JBColor.foreground()
  }

  @NeedsIndex.ForStandardLibrary
  void "test seemingly scrambled subclass"() {
    PsiTestUtil.addLibrary(module, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/normal/seemsScrambled.jar")
    myFixture.configureByText 'a.java', '''import test.Books;

class Foo {{ Books.Test.v<caret> }}
'''
    myFixture.completeBasic()
    myFixture.checkResult '''import test.Books;

class Foo {{ Books.Test.v1<caret> }}
'''

  }

  @NeedsIndex.Full
  void "test different jdks in different modules"() {
    (StatisticsManager.instance as StatisticsManagerImpl).enableStatistics(myFixture.testRootDisposable)

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
