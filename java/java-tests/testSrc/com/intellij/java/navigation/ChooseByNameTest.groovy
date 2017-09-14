/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.navigation

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.ide.util.gotoByName.*
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
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
    myPopup = null
    super.tearDown()
  }

  void "test goto class order by matching degree"() {
    def startMatch = myFixture.addClass("class UiUtil {}")
    def wordSkipMatch = myFixture.addClass("class UiAbstractUtil {}")
    def camelMatch = myFixture.addClass("class UberInstructionUxTopicInterface {}")
    def middleMatch = myFixture.addClass("class BaseUiUtil {}")
    def elements = getPopupElements(new GotoClassModel2(project), "uiuti")
    assert elements == [startMatch, wordSkipMatch, camelMatch, middleMatch]
  }

  void "test disprefer start matches when prefix starts with asterisk"() {
    def startMatch = myFixture.addClass('class ITable {}')
    def endMatch = myFixture.addClass('class HappyHippoIT {}')
    def camelStartMatch = myFixture.addClass('class IntelligentTesting {}')
    def camelMiddleMatch = myFixture.addClass('class VeryIntelligentTesting {}')

    assert getPopupElements(new GotoClassModel2(project), "*IT") == [endMatch, startMatch, camelStartMatch, camelMiddleMatch]
  }

  void "test annotation syntax"() {
    def match = myFixture.addClass("@interface Anno1 {}")
    myFixture.addClass("class Anno2 {}")
    def elements = getPopupElements(new GotoClassModel2(project), "@Anno")
    assert elements == [match]
  }

  void "test no result for empty patterns"() {
    myFixture.addClass("@interface Anno1 {}")
    myFixture.addClass("class Anno2 {}")

    def popup = createPopup(new GotoClassModel2(project))
    assert calcPopupElements(popup, "") == []
    popup.close(false)

    popup = createPopup(new GotoClassModel2(project))
    assert calcPopupElements(popup, "@") == []
    popup.close(false)

    popup = createPopup(new GotoFileModel(project))
    assert calcPopupElements(popup, "foo/") == []
    popup.close(false)
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

    def elements = getPopupElements(new GotoSymbolModel2(project), "xxx")

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

    def elements = getPopupElements(new GotoSymbolModel2(project), "xxx")

    def xxx1 = null
    def xxx2 = null
    runInEdtAndWait {
      xxx1 = intf.findMethodsByName('_xxx1', false)
      xxx2 = intf.findMethodsByName('xxx2', false)
    }
    assert elements == [xxx2, xxx1]
  }

  void "test prefer exact extension matches"() {
    def m = myFixture.addFileToProject("relaunch.m", "")
    def mod = myFixture.addFileToProject("reference.mod", "")
    def elements = getPopupElements(new GotoFileModel(project), "re*.m")
    assert elements == [m, mod]
  }

  void "test consider dot-idea files out of project"() {
    def outside = myFixture.addFileToProject(".idea/workspace.xml", "")
    def inside = myFixture.addFileToProject("workspace.txt", "")
    assert getPopupElements(new GotoFileModel(project), "work", false) == [inside]
    assert getPopupElements(new GotoFileModel(project), "work", true) == [inside, outside]
  }

  void "test prefer better path matches"() {
    def fooIndex = myFixture.addFileToProject("foo/index.html", "foo")
    def fooBarIndex = myFixture.addFileToProject("foo/bar/index.html", "foo bar")
    def barFooIndex = myFixture.addFileToProject("bar/foo/index.html", "bar foo")
    def elements = getPopupElements(new GotoFileModel(project), "foo/index")
    assert elements == [fooIndex, barFooIndex, fooBarIndex]
  }

  void "test sort same-named items by path"() {
    def files = (30..10).collect { i -> myFixture.addFileToProject("foo$i/index.html", "foo$i") }.reverse()
    def elements = getPopupElements(new GotoFileModel(project), "index")
    assert elements == files
  }

  void "test middle matching for directories"() {
    def fooIndex = myFixture.addFileToProject("foo/index.html", "foo")
    def ooIndex = myFixture.addFileToProject("oo/index.html", "oo")
    def fooBarIndex = myFixture.addFileToProject("foo/bar/index.html", "foo bar")
    def elements = getPopupElements(new GotoFileModel(project), "oo/index")
    assert elements == [ooIndex, fooIndex, fooBarIndex]
  }

  void "test prefer files from current directory"() {
    def fooIndex = myFixture.addFileToProject("foo/index.html", "foo")
    def barIndex = myFixture.addFileToProject("bar/index.html", "bar")
    def fooContext = myFixture.addFileToProject("foo/context.html", "")
    def barContext = myFixture.addFileToProject("bar/context.html", "")

    def popup = createPopup(new GotoFileModel(project), fooContext)
    assert calcPopupElements(popup, "index") == [fooIndex, barIndex]
    popup.close(false)

    popup = createPopup(new GotoFileModel(project), barContext)
    assert calcPopupElements(popup, "index") == [barIndex, fooIndex]
  }

  void "test accept file paths starting with a dot"() {
    def file = myFixture.addFileToProject("foo/index.html", "foo")
    def popup = createPopup(new GotoFileModel(project))
    assert calcPopupElements(popup, "./foo/in") == [file]
  }

  void "test don't match path to jdk"() {
    def objects = getPopupElements(new GotoFileModel(project), "Object.java", true)
    assert objects.size() > 0
    assert (objects[0] as PsiFile).virtualFile.path.contains("mockJDK")
    assert getPopupElements(new GotoFileModel(project), "mockJDK/Object.java", true).size() == 0
  }

  void "test goto file can go to dir"() {
    PsiFile fooIndex = myFixture.addFileToProject("foo/index.html", "foo")
    PsiFile barIndex = myFixture.addFileToProject("bar.txt/bar.txt", "foo")

    def popup = createPopup(new GotoFileModel(project), fooIndex)

    def fooDir = null
    def barDir = null
    runInEdtAndWait {
      fooDir = fooIndex.containingDirectory
      barDir = barIndex.containingDirectory
    }

    assert calcPopupElements(popup, "foo/") == [fooDir]
    assert calcPopupElements(popup, "foo\\") == [fooDir]
    assert calcPopupElements(popup, "/foo") == [fooDir]
    assert calcPopupElements(popup, "\\foo") == [fooDir]
    assert calcPopupElements(popup, "foo") == []
    assert calcPopupElements(popup, "/index.html") == [fooIndex]
    assert calcPopupElements(popup, "\\index.html") == [fooIndex]
    assert calcPopupElements(popup, "index.html/") == [fooIndex]
    assert calcPopupElements(popup, "index.html\\") == [fooIndex]

    assert calcPopupElements(popup, "bar.txt/") == [barDir]
    assert calcPopupElements(popup, "bar.txt\\") == [barDir]
    assert calcPopupElements(popup, "/bar.txt") == [barDir]
    assert calcPopupElements(popup, "\\bar.txt") == [barDir]
    assert calcPopupElements(popup, "bar.txt") == [barIndex]
    popup.close(false)
  }

  void "test find method by qualified name"() {
    def clazz = myFixture.addClass("package foo.bar; class Goo { void zzzZzz() {} }")
    def method = clazz.methods[0]
    assert getPopupElements(new GotoSymbolModel2(project), 'zzzZzz') == [method]
    assert getPopupElements(new GotoSymbolModel2(project), 'goo.zzzZzz') == [method]
    assert getPopupElements(new GotoSymbolModel2(project), 'foo.bar.goo.zzzZzz') == [method]
    assert getPopupElements(new GotoSymbolModel2(project), 'foo.zzzZzz') == [method]
    assert getPopupElements(new GotoSymbolModel2(project), 'bar.zzzZzz') == [method]
    assert getPopupElements(new GotoSymbolModel2(project), 'bar.goo.zzzZzz') == [method]
  }

  void "test line and column suffix"() {
    def c = myFixture.addClass("package foo; class Bar {}")
    assert getPopupElements(new GotoClassModel2(project), 'Bar') == [c]
    assert getPopupElements(new GotoClassModel2(project), 'Bar:2') == [c]
    assert getPopupElements(new GotoClassModel2(project), 'Bar:2:3') == [c]
    assert getPopupElements(new GotoClassModel2(project), 'Bar:[2:3]') == [c]
    assert getPopupElements(new GotoClassModel2(project), 'Bar:[2,3]') == [c]
  }

  void "test custom line suffixes"() {
    def file = myFixture.addFileToProject("Bar.txt", "")
    def model = new GotoFileModel(project)
    assert getPopupElements(model, 'Bar:2') == [file]
    assert getPopupElements(model, 'Bar(2)') == [file]
    assert getPopupElements(model, 'Bar on line 2') == [file]
    assert getPopupElements(model, 'Bar at line 2') == [file]
    assert getPopupElements(model, 'Bar 2:39') == [file]
    assert getPopupElements(model, 'Bar#L2') == [file]
    assert getPopupElements(model, 'Bar?l=2') == [file]
  }

  void "test dollar"() {
    def bar = myFixture.addClass("package foo; class Bar { class Foo {} }")
    def foo = bar.innerClasses[0]
    myFixture.addClass("package goo; class Goo { }")
    assert getPopupElements(new GotoClassModel2(project), 'Bar$Foo') == [foo]
    assert getPopupElements(new GotoClassModel2(project), 'foo.Bar$Foo') == [foo]
    assert getPopupElements(new GotoClassModel2(project), 'foo.B$F') == [foo]
    assert !getPopupElements(new GotoClassModel2(project), 'foo$Foo')
    assert !getPopupElements(new GotoClassModel2(project), 'foo$Bar')
    assert !getPopupElements(new GotoClassModel2(project), 'foo$Bar$Foo')
    assert !getPopupElements(new GotoClassModel2(project), 'foo$Goo')
  }

  void "test anonymous classes"() {
    def goo = myFixture.addClass("package goo; class Goo { Runnable r = new Runnable() {}; }")
    assert getPopupElements(new GotoClassModel2(project), 'Goo$1') == [goo]
    assert getPopupElements(new GotoSymbolModel2(project), 'Goo$1') == [goo]
  }

  void "test qualified name matching"() {
    def bar = myFixture.addClass("package foo.bar; class Bar { }")
    def bar2 = myFixture.addClass("package goo.baz; class Bar { }")
    assert getPopupElements(new GotoClassModel2(project), 'foo.Bar') == [bar]
    assert getPopupElements(new GotoClassModel2(project), 'foo.bar.Bar') == [bar]
    assert getPopupElements(new GotoClassModel2(project), 'goo.Bar') == [bar2]
    assert getPopupElements(new GotoClassModel2(project), 'goo.baz.Bar') == [bar2]
  }

  void "test try lowercase pattern if nothing matches"() {
    def match = myFixture.addClass("class IPRoi { }")
    def nonMatch = myFixture.addClass("class InspectionProfileImpl { }")
    assert getPopupElements(new GotoClassModel2(project), 'IPRoi') == [match]
    assert getPopupElements(new GotoClassModel2(project), 'IproImpl') == [nonMatch]
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

    def withLibs = filterJavaItems(getPopupElements(new GotoSymbolModel2(project), 'run ', true))
    withLibs.remove(sdkRun2)
    withLibs.remove(sdkRun3)
    assert withLibs == [sdkRun]
    assert !(ourRun in withLibs)

    def noLibs = filterJavaItems(getPopupElements(new GotoSymbolModel2(project), 'run ', false))
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

    assert getPopupElements(new GotoSymbolModel2(project), 'Ba.xpai', false) == [base]
    assert getPopupElements(new GotoSymbolModel2(project), 'Su.xpai', false) == [sub]
  }

  void "test groovy script class with non-identifier name"() {
    GroovyFile file1 = myFixture.addFileToProject('foo.groovy', '') as GroovyFile
    GroovyFile file2 = myFixture.addFileToProject('foo-bar.groovy', '') as GroovyFile

    def variants = getPopupElements(new GotoSymbolModel2(project), 'foo', false)
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
    def elements = getPopupElements(new GotoFileModel(project), "Object.class", true)
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
    def fooBarFile = myFixture.addFileToProject("foo/bar/index_fooBar.html", "")
    def fbFile = myFixture.addFileToProject("fb/index_fb.html", "")
    def fbSomeFile = myFixture.addFileToProject("fb/some.dir/index_fbSome.html", "")
    def someFbFile = myFixture.addFileToProject("some/fb/index_someFb.html", "")

    def popup = createPopup(new GotoFileModel(project))
    assert calcPopupElements(popup, "barindex") == [fooBarFile]
    assert calcPopupElements(popup, "fooindex") == [fooBarFile]
    assert calcPopupElements(popup, "fbindex") == [fbFile, someFbFile, fooBarFile, fbSomeFile]
    assert calcPopupElements(popup, "fbhtml") == [fbFile, someFbFile, fbSomeFile, fooBarFile]

    // partial slashes
    assert calcPopupElements(popup, "somefb/index.html") == [someFbFile]
    assert calcPopupElements(popup, "somefb\\index.html") == [someFbFile]
  }

  void "test prefer exact case match"() {
    def upper = myFixture.addClass("class SOMECLASS {}")
    def camel = myFixture.addClass("class SomeClass {}")
    assert getPopupElements(new GotoClassModel2(project), 'SomeClass') == [camel, upper]
    assert getPopupElements(new GotoFileModel(project), 'SomeClass.java') == [camel.containingFile, upper.containingFile]
  }

  private List<Object> getPopupElements(ChooseByNameModel model, String text, boolean checkboxState = false) {
    return calcPopupElements(createPopup(model), text, checkboxState)
  }

  static ArrayList<Object> calcPopupElements(ChooseByNamePopup popup, String text, boolean checkboxState = false) {
    List<Object> elements = ['empty']
    def semaphore = new Semaphore(1)
    popup.scheduleCalcElements(text, checkboxState, ModalityState.NON_MODAL, { set ->
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

    runInEdtAndWait {
      def popup = myPopup = ChooseByNamePopup.createPopup(project, model, (PsiElement)context, "")
      Disposer.register(myFixture.testRootDisposable, { popup.close(false) } as Disposable)
    }
    myPopup
  }

}