/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.navigation

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.lang.java.JavaLanguage
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ObjectUtils
import com.intellij.util.indexing.FindSymbolParameters
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait

/**
 * @author peter
 */
class ChooseByNameTest extends LightJavaCodeInsightFixtureTestCase {

  static final ELEMENTS_LIMIT = 30

  @Override
  protected void tearDown() throws Exception {
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

  void "test class a in same-named package and partially matching subpackage"() {
    def c = myFixture.addClass("package com.intellij.codeInsight.template.impl; class TemplateListPanel {}")
    assert gotoClass("templistpa") == [c]
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

  void "test goto symbol by Copy Reference result"() {
    def methods = myFixture.addClass('''
package pkg; 
import java.util.*; 
class Cls { 
  void foo(int i) {} 
  void bar(int j) {} 
  void bar(boolean b) {} 
  void bar(List<String> l) {} 
}''').methods
    assert gotoSymbol('pkg.Cls.foo') == [methods[0]]
    assert gotoSymbol('pkg.Cls#foo') == [methods[0]]
    assert gotoSymbol('pkg.Cls#foo(int)') == [methods[0]]

    assert gotoSymbol('pkg.Cls.bar') as Set == methods[1..3] as Set
    assert gotoSymbol('pkg.Cls#bar') as Set == methods[1..3] as Set

    assert gotoSymbol('pkg.Cls#bar(int)') == [methods[1]]
    assert gotoSymbol('pkg.Cls#bar(boolean)') == [methods[2]]
    assert gotoSymbol('pkg.Cls#bar(java.util.List)') == [methods[3]]
    assert gotoSymbol('pkg.Cls#bar(java.util.List<java.lang.String>)') == [methods[3]]
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

  void "test prefer shorter filename match"() {
    def shorter = addEmptyFile("foo/cp-users.txt")
    def longer = addEmptyFile("cp-users-and-smth.html")
    assert gotoFile('cpusers') == [shorter, longer]
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

    def contributor = createFileContributor(project, fooContext)
    assert calcContributorElements(contributor, "index") == [fooIndex, barIndex]

    contributor = createFileContributor(project, barContext)
    assert calcContributorElements(contributor, "index") == [barIndex, fooIndex]
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

    def contributor = createFileContributor(project, fooIndex)

    def fooDir = fooIndex.containingDirectory
    def barDir = barIndex.containingDirectory

    assert calcContributorElements(contributor, "foo/") == [fooDir]
    assert calcContributorElements(contributor, "foo\\") == [fooDir]
    assert calcContributorElements(contributor, "/foo") == [fooDir]
    assert calcContributorElements(contributor, "\\foo") == [fooDir]
    assert calcContributorElements(contributor, "foo") == [fooDir]
    assert calcContributorElements(contributor, "/index.html") == [fooIndex]
    assert calcContributorElements(contributor, "\\index.html") == [fooIndex]
    assert calcContributorElements(contributor, "index.html/") == []
    assert calcContributorElements(contributor, "index.html\\") == []

    assert calcContributorElements(contributor, "bar.txt/") == [barDir]
    assert calcContributorElements(contributor, "bar.txt\\") == [barDir]
    assert calcContributorElements(contributor, "/bar.txt") == [barIndex, barDir]
    assert calcContributorElements(contributor, "\\bar.txt") == [barIndex, barDir]
    assert calcContributorElements(contributor, "bar.txt") == [barIndex, barDir]
    assert calcContributorElements(contributor, "bar") == [barIndex, barDir]
  }

  void "test prefer files to directories even if longer"() {
    def fooFile = addEmptyFile('dir/fooFile.txt')
    def fooDir = addEmptyFile('foo/barFile.txt').containingDirectory

    def contributor = createFileContributor(project)
    def popupElements = calcContributorElements(contributor, 'foo')

    assert popupElements == [fooFile, fooDir]
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

  private static filterJavaItems(List<Object> items) {
    return items.findAll { it instanceof PsiElement && it.language == JavaLanguage.INSTANCE }
  }

  void "test super method in jdk"() {
    def clazz = myFixture.addClass("package foo.bar; class Goo implements Runnable { public void run() {} }")
    def ourRun = clazz.methods[0]
    def sdkRun = ourRun.containingClass.interfaces[0].methods[0]
    def sdkRun2 = myFixture.findClass("java.security.PrivilegedAction").methods[0]
    def sdkRun3 = myFixture.findClass("java.security.PrivilegedExceptionAction").methods[0]

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
    def contributor = createClassContributor(project)
    def popupElements = calcContributorElements(contributor, 'xfile')

    assert popupElements == [wanted, smth2, smth1]
  }

  void "test prefer prefix match"() {
    def wanted = myFixture.addClass('class PsiClassImpl {}')
    def smth = myFixture.addClass('class DroolsPsiClassImpl {}')
    def contributor = createClassContributor(project)
    def popupElements = calcContributorElements(contributor, 'PsiCl')

    assert popupElements == [wanted, smth]
  }

  void "test out-of-project-content files"() {
    def file = myFixture.findClass(CommonClassNames.JAVA_LANG_OBJECT).containingFile
    def elements = gotoFile("Object.class", true)
    assert file in elements
  }

  void "test classes sorted by qualified name dispreferring excluded from import and completion"() {
    def foo = myFixture.addClass('package foo; class List {}')
    def bar = myFixture.addClass('package bar; class List {}')

    def contributor = createClassContributor(project, myFixture.addClass('class Context {}').containingFile)
    assert calcContributorElements(contributor, "List") == [bar, foo]

    JavaProjectCodeInsightSettings.setExcludedNames(project, testRootDisposable, 'bar')
    assert calcContributorElements(contributor, "List") == [foo, bar]
  }

  void "test file path matching without slashes"() {
    def fooBarFile = addEmptyFile("foo/bar/index_fooBar.html")
    def fbFile = addEmptyFile("fb/index_fb.html")
    def fbSomeFile = addEmptyFile("fb/some.dir/index_fbSome.html")
    def someFbFile = addEmptyFile("some/fb/index_someFb.html")

    assert gotoFile("barindex") == [fooBarFile]
    assert gotoFile("fooindex") == [fooBarFile]
    assert gotoFile("fbindex") == [fbFile, someFbFile, fbSomeFile, fooBarFile]
    assert gotoFile("fbhtml") == [fbFile, someFbFile, fbSomeFile, fooBarFile]

    // partial slashes
    assert gotoFile("somefb/index.html") == [someFbFile]
    assert gotoFile("somefb\\index.html") == [someFbFile]
  }

  void "test file path matching with spaces instead of slashes"() {
    def good = addEmptyFile("config/app.txt")
    addEmptyFile("src/Configuration/ManagesApp.txt")

    assert gotoFile("config app.txt")[0] == good
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
    def charSeq = myFixture.findClass(CharSequence.name)
    assert gotoFile('langcsequence', true) == [charSeq.containingFile, seq]
  }

  void "test show no matches from jdk when there are in project"() {
    def file = addEmptyFile("String.txt")
    assert gotoFile('Str', false) == [file]
  }

  void "test fix keyboard layout"() {
    assert (gotoClass('Ыекштп', true)[0] as PsiClass).name == 'String'
    assert (gotoSymbol('Ыекштп', true).find { it instanceof PsiClass && it.name == 'String' })
    assert (gotoFile('Ыекштп', true)[0] as PsiFile).name == 'String.class'
    assert (gotoFile('дфтпЫекштп', true)[0] as PsiFile).name == 'String.class'
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

  void "test matching file in a matching directory"() {
    def file = addEmptyFile("foo/index/index")
    assert gotoFile('in') == [file, file.parent]
    assert gotoFile('foin') == [file, file.parent]
  }

  void "test prefer fully matching module name"() {
    def module = myFixture.addFileToProject('module-info.java', 'module foo.bar {}')
    def clazz = myFixture.addClass('package foo; class B { void bar() {} void barX() {} }')
    assert gotoSymbol('foo.bar') == [(module as PsiJavaFile).moduleDeclaration, clazz.methods[0], clazz.methods[1]]
  }

  void "test allow name separators inside wildcard"() {
    def clazz = myFixture.addClass('package foo; class X { void bar() {} }')
    assert gotoSymbol('foo*bar') == [clazz.methods[0]]
    assert gotoClass('foo*X') == [clazz]
    assert gotoClass('X') == [clazz]
    assert gotoClass('foo.*') == [clazz]
  }

  void "test prefer longer name vs qualifier matches"() {
    def myInspection = myFixture.addClass('package ss; class MyInspection { }')
    def ssBasedInspection = myFixture.addClass('package foo; class SSBasedInspection { }')
    assert gotoClass('ss*inspection') == [ssBasedInspection, myInspection]
  }

  void "test show all same-named classes sorted by qname"() {
    def aFoo = myFixture.addClass('package a; class Foo { }')
    def bFoo = myFixture.addClass('package b; class Foo { }')
    def fooBar = myFixture.addClass('package c; class FooBar { }')
    assert gotoClass('Foo') == [aFoo, bFoo, fooBar]
  }

  void "test show prefix matches first when asterisk is in the middle"() {
    def sb = myFixture.findClass(StringBuilder.name)
    def asb = myFixture.findClass('java.lang.AbstractStringBuilder')
    assert gotoClass('Str*Builder', true) == [sb, asb]
    assert gotoClass('java.Str*Builder', true) == [sb, asb]
  }

  void "test include overridden qualified name method matches"() {
    def m1 = myFixture.addClass('interface HttpRequest { void start() {} }').methods[0]
    def m2 = myFixture.addClass('interface Request extends HttpRequest { void start() {} }').methods[0]
    assert gotoSymbol('Request.start') == [m1, m2]
    assert gotoSymbol('start') == [m1] // works as usual for non-qualified patterns
  }

  void "test colon in search end"() {
    def foo = myFixture.addClass('class Foo { }')
    assert gotoClass('Foo:') == [foo]
  }

  void "test multi-word class name with only first letter of second word"() {
    myFixture.addClass('class Foo { }')
    def fooBar = myFixture.addClass('class FooBar { }')
    assert gotoClass('Foo B') == [fooBar]
  }

  void "test prefer filename match regardless of package match"() {
    def f1 = addEmptyFile('resolve/ResolveCache.java')
    def f2 = addEmptyFile('abc/ResolveCacheSettings.xml')
    assert gotoFile('resolvecache') == [f1, f2]
  }

  void "test search for long full name"() {
    def veryLongNameFile = addEmptyFile("aaaaaaaaaaaaaaaaa/bbbbbbbbbbbbbbbb/cccccccccccccccccc/" +
                                        "ddddddddddddddddd/eeeeeeeeeeeeeeee/ffffffffffffffffff/" +
                                        "ggggggggggggggggg/hhhhhhhhhhhhhhhh/ClassName.java")

    assert gotoFile("bbbbbbbbbbbbbbbb/cccccccccccccccccc/ddddddddddddddddd/eeeeeeeeeeeeeeee/" +
                    "ffffffffffffffffff/ggggggggggggggggg/hhhhhhhhhhhhhhhh/ClassName.java") == [veryLongNameFile]
  }

  private List<Object> gotoClass(String text, boolean checkboxState = false, PsiElement context = null) {
    return getContributorElements(createClassContributor(project, context, checkboxState), text)
  }

  private List<Object> gotoSymbol(String text, boolean checkboxState = false, PsiElement context = null) {
    return getContributorElements(createSymbolContributor(project, context, checkboxState), text)
  }

  private List<Object> gotoFile(String text, boolean checkboxState = false, PsiElement context = null) {
    return getContributorElements(createFileContributor(project, context, checkboxState), text)
  }

  private static List<Object> getContributorElements(SearchEverywhereContributor<?> contributor, String text) {
    return calcContributorElements(contributor, text)
  }

  static List<Object> calcContributorElements(SearchEverywhereContributor<?> contributor, String text) {
    return contributor.search(text, new MockProgressIndicator(), ELEMENTS_LIMIT).items
  }

  static SearchEverywhereContributor<Object> createClassContributor(Project project, PsiElement context = null, boolean everywhere = false) {
    def res = new TestClassContributor(createEvent(project, context))
    res.setEverywhere(everywhere)
    Disposer.register(project, res)
    return res
  }

  static SearchEverywhereContributor<Object> createFileContributor(Project project, PsiElement context = null, boolean everywhere = false) {
    def res = new TestFileContributor(createEvent(project, context))
    res.setEverywhere(everywhere)
    Disposer.register(project, res)
    return res
  }

  static SearchEverywhereContributor<Object> createSymbolContributor(Project project, PsiElement context = null, boolean everywhere = false) {
    def res = new TestSymbolContributor(createEvent(project, context))
    res.setEverywhere(everywhere)
    Disposer.register(project, res)
    return res
  }

  static AnActionEvent createEvent(Project project, PsiElement context = null) {
    def dataContext = SimpleDataContext.getSimpleContext(
      CommonDataKeys.PSI_FILE.name, ObjectUtils.tryCast(context, PsiFile.class), SimpleDataContext.getProjectContext(project))
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)
  }

  private static class TestClassContributor extends ClassSearchEverywhereContributor {

    TestClassContributor(@NotNull AnActionEvent event) {
      super(event)
    }

    void setEverywhere(boolean state) {
      myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(myProject, state))
    }
  }

  private static class TestFileContributor extends FileSearchEverywhereContributor {

    TestFileContributor(@NotNull AnActionEvent event) {
      super(event)
    }

    void setEverywhere(boolean state) {
      myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(myProject, state))
    }
  }

  private static class TestSymbolContributor extends SymbolSearchEverywhereContributor {

    TestSymbolContributor(@NotNull AnActionEvent event) {
      super(event)
    }

    void setEverywhere(boolean state) {
      myScopeDescriptor = new ScopeDescriptor(FindSymbolParameters.searchScopeFor(myProject, state))
    }
  }
}