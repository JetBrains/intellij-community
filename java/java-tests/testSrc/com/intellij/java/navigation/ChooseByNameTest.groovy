/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.navigation

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.ide.util.gotoByName.*
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ModalityState
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.Consumer
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait

/**
 * @author peter
 */
class ChooseByNameTest extends LightCodeInsightFixtureTestCase {
  ChooseByNamePopup myPopup

  @Override
  protected void tearDown() throws Exception {
    myPopup?.close(false)
    myPopup = null
    super.tearDown()
  }

  void "test goto class order by matching degree"() {
    def startMatch = myFixture.addClass("class UiUtil {}")
    def wordSkipMatch = myFixture.addClass("class UiAbstractUtil {}")
    def camelMatch = myFixture.addClass("class UberInstructionUxTopicInterface {}")
    def middleMatch = myFixture.addClass("class BaseUiUtil {}")
    def elements = gotoClass("uiuti")
    assert elements == [startMatch, wordSkipMatch, camelMatch, middleMatch]
  }

  void "test goto file order by matching degree"() {
    def camel = addEmptyFile("ServiceAccessor.java")
    def startLower = addEmptyFile("sache.txt")
    assert gotoFile('SA') == [camel, startLower]
  }

  void "test disprefer start matches when prefix starts with asterisk"() {
    def startMatch = myFixture.addClass('class ITable {}')
    def endMatch = myFixture.addClass('class HappyHippoIT {}')
    def camelStartMatch = myFixture.addClass('class IntelligentTesting {}')
    def camelMiddleMatch = myFixture.addClass('class VeryIntelligentTesting {}')

    assert gotoClass("*IT") == [endMatch, startMatch, camelStartMatch, camelMiddleMatch]
  }

  void "test annotation syntax"() {
    def match = myFixture.addClass("@interface Anno1 {}")
    myFixture.addClass("class Anno2 {}")
    assert gotoClass("@Anno") == [match]
  }

  void "test no result for empty patterns"() {
    myFixture.addClass("@interface Anno1 {}")
    myFixture.addClass("class Anno2 {}")

    assert gotoClass("") == []
    assert gotoClass("@") == []
    assert gotoFile("foo/") == []
  }

  void "test filter overridden methods from goto symbol"() {
    def intf = myFixture.addClass("""
class Intf {
  void xxx1() {}
  void xxx2() {}
}""")
    def impl = myFixture.addClass("""
class Impl extends Intf {
    void xxx1() {}
    void xxx3() {}
}
""")

    def elements = gotoSymbol("xxx")

    assert intf.findMethodsByName('xxx1', false)[0] in elements
    assert intf.findMethodsByName('xxx2', false)[0] in elements

    assert impl.findMethodsByName('xxx3', false)[0] in elements
    assert !(impl.findMethodsByName('xxx1', false)[0] in elements)
  }

  void "test disprefer underscore"() {
    def intf = myFixture.addClass("""
class Intf {
  void _xxx1() {}
  void xxx2() {}
}""")

    def elements = gotoSymbol("xxx")

    def xxx1 = null
    def xxx2 = null
    runInEdtAndWait {
      xxx1 = intf.findMethodsByName('_xxx1', false)
      xxx2 = intf.findMethodsByName('xxx2', false)
    }
    assert elements == [xxx2, xxx1]
  }

  void "test prefer exact extension matches"() {
    def m = addEmptyFile("relaunch.m")
    def mod = addEmptyFile("reference.mod")
    assert gotoFile('re*.m') == [m, mod]
  }

  void "test prefer exact filename match"() {
    def i = addEmptyFile("foo/i.txt")
    def index = addEmptyFile("index.html")
    assert gotoFile('i') == [i, index]
  }

  void "test consider dot-idea files out of project"() {
    def outside = addEmptyFile(".idea/workspace.xml")
    def inside = addEmptyFile("workspace.txt")
    assert gotoFile("work", false) == [inside]
    assert gotoFile("work", true) == [inside, outside]
  }

  void "test prefer better path matches"() {
    def fooIndex = myFixture.addFileToProject("foo/index.html", "foo")
    def fooBarIndex = myFixture.addFileToProject("foo/bar/index.html", "foo bar")
    def barFooIndex = myFixture.addFileToProject("bar/foo/index.html", "bar foo")
    assert gotoFile("foo/index") == [fooIndex, barFooIndex, fooBarIndex]
  }

  void "test sort same-named items by path"() {
    def files = (30..10).collect { i -> myFixture.addFileToProject("foo$i/index.html", "foo$i") }.reverse()
    assert gotoFile('index') == files
  }

  void "test middle matching for files and directories"() {
    def fooIndex = myFixture.addFileToProject("foo/index.html", "foo")
    def ooIndex = myFixture.addFileToProject("oo/index.html", "oo")
    def fooBarIndex = myFixture.addFileToProject("foo/bar/index.html", "foo bar")
    assert gotoFile("oo/index") == [ooIndex, fooIndex, fooBarIndex]
    assert gotoFile("ndex.html") == [fooIndex, ooIndex, fooBarIndex]
  }

  void "test prefer files from current directory"() {
    def fooIndex = myFixture.addFileToProject("foo/index.html", "foo")
    def barIndex = myFixture.addFileToProject("bar/index.html", "bar")
    def fooContext = addEmptyFile("foo/context.html")
    def barContext = addEmptyFile("bar/context.html")

    def popup = createPopup(new GotoFileModel(project), fooContext)
    assert calcPopupElements(popup, "index") == [fooIndex, barIndex]
    popup.close(false)

    popup = createPopup(new GotoFileModel(project), barContext)
    assert calcPopupElements(popup, "index") == [barIndex, fooIndex]
  }

  private PsiFile addEmptyFile(String relativePath) {
    return myFixture.addFileToProject(relativePath, "")
  }

  void "test accept file paths starting with a dot"() {
    def file = addEmptyFile("foo/index.html")
    assert gotoFile("./foo/in") == [file]
  }

  void "test don't match path to jdk"() {
    def objects = gotoFile("Object.java", true)
    assert objects.size() > 0
    assert (objects[0] as PsiFile).virtualFile.path.contains("mockJDK")
    assert gotoFile("mockJDK/Object.java", true).size() == 0
  }

  void "test goto file can go to dir"() {
    PsiFile fooIndex = addEmptyFile("foo/index.html")
    PsiFile barIndex = addEmptyFile("bar.txt/bar.txt")

    def popup = createPopup(new GotoFileModel(project), fooIndex)

    def fooDir = fooIndex.containingDirectory
    def barDir = barIndex.containingDirectory

    assert calcPopupElements(popup, "foo/") == [fooDir]
    assert calcPopupElements(popup, "foo\\") == [fooDir]
    assert calcPopupElements(popup, "/foo") == [fooDir]
    assert calcPopupElements(popup, "\\foo") == [fooDir]
    assert calcPopupElements(popup, "foo") == [fooDir]
    assert calcPopupElements(popup, "/index.html") == [fooIndex]
    assert calcPopupElements(popup, "\\index.html") == [fooIndex]
    assert calcPopupElements(popup, "index.html/") == []
    assert calcPopupElements(popup, "index.html\\") == []

    assert calcPopupElements(popup, "bar.txt/") == [barDir]
    assert calcPopupElements(popup, "bar.txt\\") == [barDir]
    assert calcPopupElements(popup, "/bar.txt") == [barIndex, barDir]
    assert calcPopupElements(popup, "\\bar.txt") == [barIndex, barDir]
    assert calcPopupElements(popup, "bar.txt") == [barIndex, barDir]
    assert calcPopupElements(popup, "bar") == [barIndex, barDir]
    popup.close(false)
  }

  void "test prefer files to directories even if longer"() {
    def fooFile = addEmptyFile('dir/fooFile.txt')
    def fooDir = addEmptyFile('foo/barFile.txt').containingDirectory

    def popup = createPopup(new GotoFileModel(project))
    def popupElements = calcPopupElements(popup, 'foo')

    assert popupElements == [fooFile, fooDir]
    assert popup.calcSelectedIndex(popupElements.toArray(), 'foo') == 0
  }

  void "test find method by qualified name"() {
    def clazz = myFixture.addClass("package foo.bar; class Goo { void zzzZzz() {} }")
    def method = clazz.methods[0]
    assert gotoSymbol('zzzZzz') == [method]
    assert gotoSymbol('goo.zzzZzz') == [method]
    assert gotoSymbol('foo.bar.goo.zzzZzz') == [method]
    assert gotoSymbol('foo.zzzZzz') == [method]
    assert gotoSymbol('bar.zzzZzz') == [method]
    assert gotoSymbol('bar.goo.zzzZzz') == [method]
  }

  void "test line and column suffix"() {
    def c = myFixture.addClass("package foo; class Bar {}")
    assert gotoClass('Bar') == [c]
    assert gotoClass('Bar:2') == [c]
    assert gotoClass('Bar:2:3') == [c]
    assert gotoClass('Bar:[2:3]') == [c]
    assert gotoClass('Bar:[2,3]') == [c]
  }

  void "test custom line suffixes"() {
    def file = addEmptyFile("Bar.txt")
    assert gotoFile('Bar:2') == [file]
    assert gotoFile('Bar(2)') == [file]
    assert gotoFile('Bar on line 2') == [file]
    assert gotoFile('Bar at line 2') == [file]
    assert gotoFile('Bar 2:39') == [file]
    assert gotoFile('Bar#L2') == [file]
    assert gotoFile('Bar?l=2') == [file]
  }

  void "test dollar"() {
    def bar = myFixture.addClass("package foo; class Bar { class Foo {} }")
    def foo = bar.innerClasses[0]
    myFixture.addClass("package goo; class Goo { }")
    assert gotoClass('Bar$Foo') == [foo]
    assert gotoClass('foo.Bar$Foo') == [foo]
    assert gotoClass('foo.B$F') == [foo]
    assert !gotoClass('foo$Foo')
    assert !gotoClass('foo$Bar')
    assert !gotoClass('foo$Bar$Foo')
    assert !gotoClass('foo$Goo')
  }

  void "test anonymous classes"() {
    def goo = myFixture.addClass("package goo; class Goo { Runnable r = new Runnable() {}; }")
    assert gotoClass('Goo$1') == [goo]
    assert gotoSymbol('Goo$1') == [goo]
  }

  void "test qualified name matching"() {
    def bar = myFixture.addClass("package foo.bar; class Bar { }")
    def bar2 = myFixture.addClass("package goo.baz; class Bar { }")
    assert gotoClass('foo.Bar') == [bar]
    assert gotoClass('foo.bar.Bar') == [bar]
    assert gotoClass('goo.Bar') == [bar2]
    assert gotoClass('goo.baz.Bar') == [bar2]
  }

  void "test try lowercase pattern if nothing matches"() {
    def match = myFixture.addClass("class IPRoi { }")
    def nonMatch = myFixture.addClass("class InspectionProfileImpl { }")
    assert gotoClass('IPRoi') == [match]
    assert gotoClass('IproImpl') == [nonMatch]
  }

  private static filterJavaItems(List<Object> items) {
    return items.findAll { it instanceof PsiElement && it.language == JavaLanguage.INSTANCE }
  }

  void "test super method in jdk"() {
    def clazz = myFixture.addClass("package foo.bar; class Goo implements Runnable { public void run() {} }")
    def ourRun = null
    def sdkRun = null
    def sdkRun2 = null
    def sdkRun3 = null
    runInEdtAndWait {
      ourRun = clazz.methods[0]
      sdkRun = ourRun.containingClass.interfaces[0].methods[0]
      sdkRun2 = myFixture.javaFacade.findClass("java.security.PrivilegedAction").methods[0]
      sdkRun3 = myFixture.javaFacade.findClass("java.security.PrivilegedExceptionAction").methods[0]
    }

    def withLibs = filterJavaItems(gotoSymbol('run ', true))
    withLibs.remove(sdkRun2)
    withLibs.remove(sdkRun3)
    assert withLibs == [sdkRun]
    assert !(ourRun in withLibs)

    def noLibs = filterJavaItems(gotoSymbol('run ', false))
    assert noLibs == [ourRun]
    assert !(sdkRun in noLibs)
  }

  void "test super method not matching query qualifier"() {
    def baseClass = myFixture.addClass("class Base { void xpaint() {} }")
    def subClass = myFixture.addClass("class Sub extends Base { void xpaint() {} }")

    def base = null
    def sub = null
    runInEdtAndWait {
      base = baseClass.methods[0]
      sub = subClass.methods[0]
    }

    assert gotoSymbol('Ba.xpai', false) == [base]
    assert gotoSymbol('Su.xpai', false) == [sub]
  }

  void "test groovy script class with non-identifier name"() {
    GroovyFile file1 = addEmptyFile('foo.groovy') as GroovyFile
    GroovyFile file2 = addEmptyFile('foo-bar.groovy') as GroovyFile

    def variants = gotoSymbol('foo', false)
    runInEdtAndWait { assert variants == [file1.scriptClass, file2.scriptClass] }
  }

  void "test prefer case-insensitive exact prefix match"() {
    def wanted = myFixture.addClass('class XFile {}')
    def smth1 = myFixture.addClass('class xfilterExprOwner {}')
    def smth2 = myFixture.addClass('class xfile_baton_t {}')
    def popup = createPopup(new GotoClassModel2(project))
    def popupElements = calcPopupElements(popup, 'xfile', false)

    assert popupElements == [wanted, smth2, smth1]
    assert popup.calcSelectedIndex(popupElements.toArray(), 'xfile') == 0
  }

  void "test prefer prefix match"() {
    def wanted = myFixture.addClass('class PsiClassImpl {}')
    def smth = myFixture.addClass('class DroolsPsiClassImpl {}')
    def popup = createPopup(new GotoClassModel2(project))
    def popupElements = calcPopupElements(popup, 'PsiCl', false)

    assert popupElements == [wanted, smth]
    assert popup.calcSelectedIndex(popupElements.toArray(), 'PsiCl') == 0
  }

  void "test out-of-project-content files"() {
    def scope = ProjectScope.getAllScope(project)
    def file = myFixture.javaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, scope).containingFile
    def elements = gotoFile("Object.class", true)
    assert file in elements
  }

  void "test classes sorted by qualified name dispreferring excluded from import and completion"() {
    def foo = myFixture.addClass('package foo; class List {}')
    def bar = myFixture.addClass('package bar; class List {}')

    def popup = createPopup(new GotoClassModel2(project), myFixture.addClass('class Context {}'))
    assert calcPopupElements(popup, "List", false) == [bar, foo]

    JavaProjectCodeInsightSettings.setExcludedNames(project, testRootDisposable, 'bar')
    assert calcPopupElements(popup, "List", false) == [foo, bar]
  }

  void "test file path matching without slashes"() {
    def fooBarFile = addEmptyFile("foo/bar/index_fooBar.html")
    def fbFile = addEmptyFile("fb/index_fb.html")
    def fbSomeFile = addEmptyFile("fb/some.dir/index_fbSome.html")
    def someFbFile = addEmptyFile("some/fb/index_someFb.html")

    assert gotoFile("barindex") == [fooBarFile]
    assert gotoFile("fooindex") == [fooBarFile]
    assert gotoFile("fbindex") == [fbFile, someFbFile, fooBarFile, fbSomeFile]
    assert gotoFile("fbhtml") == [fbFile, someFbFile, fbSomeFile, fooBarFile]

    // partial slashes
    assert gotoFile("somefb/index.html") == [someFbFile]
    assert gotoFile("somefb\\index.html") == [someFbFile]
  }

  void "test multiple slashes in goto file"() {
    def file = addEmptyFile("foo/bar/goo/file.txt")
    ['foo/goo/file.txt', 'foo/bar/file.txt', 'bar/goo/file.txt', 'foo/bar/goo/file.txt'].each {
      assert gotoFile(it) == [file]
      assert gotoFile(it.replace('/', '\\')) == [file]
    }
  }

  void "test show matches from different suffixes"() {
    def enumControl = addEmptyFile("sample/EnumControl.java")
    def control = addEmptyFile("sample/ControlSmth.java")
    assert gotoFile('samplecontrol', false) == [enumControl, control]
  }

  void "test show longer suffix matches from jdk and shorter from project"() {
    def seq = addEmptyFile("langc/Sequence.java")
    def charSeq = JavaPsiFacade.getInstance(project).findClass(CharSequence.name, GlobalSearchScope.allScope(project))
    assert gotoFile('langcsequence', false) == [charSeq.containingFile, seq]
  }

  void "test show no matches from jdk when there are in project"() {
    def file = addEmptyFile("String.txt")
    assert gotoFile('Str', false) == [file]
  }

  void "test fix keyboard layout"() {
    assert (gotoClass('Ыекштп')[0] as PsiClass).name == 'String'
    assert (gotoSymbol('Ыекштп').find { it instanceof PsiClass && it.name == 'String' })
    assert (gotoFile('Ыекштп')[0] as PsiFile).name == 'String.class'
    assert (gotoFile('дфтпЫекштп')[0] as PsiFile).name == 'String.class'
  }

  void "test prefer exact case match"() {
    def upper = myFixture.addClass("package foo; class SOMECLASS {}")
    def camel = myFixture.addClass("package bar; class SomeClass {}")
    assert gotoClass('SomeClass') == [camel, upper]
    assert gotoFile('SomeClass.java') == [camel.containingFile, upper.containingFile]
  }
  
  void "test prefer closer path match"() {
    def index = addEmptyFile("content/objc/features/index.html")
    def i18n = addEmptyFile("content/objc/features/screenshots/i18n.html")
    assert gotoFile('objc/features/i') == [index, i18n]
  }

  private List<Object> gotoClass(String text, boolean checkboxState = false) {
    return getPopupElements(new GotoClassModel2(project), text, checkboxState)
  }

  private List<Object> gotoSymbol(String text, boolean checkboxState = false) {
    return getPopupElements(new GotoSymbolModel2(project), text, checkboxState)
  }

  private List<Object> gotoFile(String text, boolean checkboxState = false) {
    return getPopupElements(new GotoFileModel(project), text, checkboxState)
  }

  private List<Object> getPopupElements(ChooseByNameModel model, String text, boolean checkboxState = false) {
    return calcPopupElements(createPopup(model), text, checkboxState)
  }

  static ArrayList<Object> calcPopupElements(ChooseByNamePopup popup, String text, boolean checkboxState = false) {
    List<Object> elements = ['empty']
    def semaphore = new Semaphore(1)
    popup.scheduleCalcElements(text, checkboxState, ModalityState.NON_MODAL, SelectMostRelevant.INSTANCE, { set ->
      elements = set as List<Object>
      semaphore.up()
    } as Consumer<Set<?>>)
    def start = System.currentTimeMillis()
    while (!semaphore.waitFor(10) && System.currentTimeMillis() - start < 10_000_000) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    if (!semaphore.waitFor(10)) {
      printThreadDump()
      fail()
    }
    return elements
  }

  private ChooseByNamePopup createPopup(ChooseByNameModel model, PsiElement context = null) {
    if (myPopup) {
      myPopup.close(false)
    }

    return myPopup = ChooseByNamePopup.createPopup(project, model, (PsiElement)context, "")
  }

}