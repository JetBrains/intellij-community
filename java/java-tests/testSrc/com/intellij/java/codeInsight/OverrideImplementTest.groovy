// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsHandler
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.ServiceContainerUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * @author ven
 */
class OverrideImplementTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/overrideImplement"
  }

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15
  }
  
  private void addRecordClass() {
    myFixture.addClass("package java.lang;public abstract class Record {" +
                       "public abstract boolean equals(Object obj);" +
                       "public abstract int hashCode();" +
                       "public abstract String toString();}")
  }

  void testImplementRecordMethods() { addRecordClass();doTest(true) }

  void testImplementInterfaceMethodsInRecord() { addRecordClass();doTest(true) }

  void testOverrideRecordMethods() { addRecordClass();doTest(false) }

  void testImplementExtensionMethods() { doTest(true) }

  void testOverrideExtensionMethods() { doTest(false) }
  void testMultipleSuperMethodsThroughGenerics() { doTest(true) }

  void testDoNotImplementExtensionMethods() { doTest(true) }
  
  void testExtensionMethods1() { doTest(true) }

  void testExtensionMethods2() { doTest(true) }

  void testSkipUnknownAnnotations() { doTest(true) }

  void testMultipleInheritedThrows() { doTest(false) }

  void testOverrideInInterface() { doTest(false) }

  void testMultipleInheritanceWithThrowables() { doTest(true) }

  void testBrokenMethodDeclaration() {
    myFixture.addClass("interface A { m();}")
    def psiClass = myFixture.addClass("class B implements A {<caret>}")
    assertEmpty(OverrideImplementExploreUtil.getMethodSignaturesToImplement(psiClass))
  }

  void testImplementInInterface() {
    myFixture.addClass """\
interface A {
    void foo();
}
"""
    def file = myFixture.addClass("""\
interface B extends A {
    <caret>
}
""").containingFile.virtualFile
    myFixture.configureFromExistingVirtualFile(file)

    Presentation presentation = new Presentation()
    presentation.setText(ActionsBundle.message("action.ImplementMethods.text"))
    CommandProcessor.instance.executeCommand(project, { invokeAction(true) }, presentation.text, null)

    myFixture.checkResult """\
interface B extends A {
    @Override
    default void foo() {
        <caret>
    }
}
"""
  }

  void testImplementInAnnotation() {
    def file = myFixture.addClass("""\
@interface A {
    <caret>
}
""").containingFile.virtualFile
    myFixture.configureFromExistingVirtualFile(file)

    Presentation presentation = new Presentation()
    presentation.setText(ActionsBundle.message("action.ImplementMethods.text"))
    CommandProcessor.instance.executeCommand(project, { invokeAction(true) }, presentation.text, null)

    myFixture.checkResult """\
@interface A {
    <caret>
}
"""
  }

  void testImplementInterfaceWhenClassProvidesProtectedImplementation() {
    myFixture.addClass """\
interface A {
  void f();
}
"""
    myFixture.addClass """\
class B {
  protected void f() {}
}
"""

    def file = myFixture.addClass("""\
class C extends B implements A {
   <caret>
}
""").containingFile.virtualFile
    myFixture.configureFromExistingVirtualFile(file)

    Presentation presentation = new Presentation()
    presentation.setText(ActionsBundle.message("action.ImplementMethods.text"))
    CommandProcessor.instance.executeCommand(project, { invokeAction(true) }, presentation.text, null)

    myFixture.checkResult """\
class C extends B implements A {
    @Override
    public void f() {
        <caret>
    }
}
"""
  }

  void testImplementSameNamedInterfaces() {
    myFixture.addClass """\
class Main1 {
   interface I {
      void foo();
   }
}
"""
    myFixture.addClass """\
class Main2 {
   interface I {
      void bar();
   }
}
"""

    def file = myFixture.addClass("""\
class B implements Main1.I, Main2.I {
    <caret>
}
""").containingFile.virtualFile
    myFixture.configureFromExistingVirtualFile(file)

    Presentation presentation = new Presentation()
    presentation.setText(ActionsBundle.message("action.ImplementMethods.text"))
    CommandProcessor.instance.executeCommand(project, { invokeAction(true) }, presentation.text, null)

    myFixture.checkResult """\
class B implements Main1.I, Main2.I {
    @Override
    public void foo() {
        <caret>
    }

    @Override
    public void bar() {

    }
}
"""
  }

  void "test overriding overloaded method"() {
    myFixture.addClass """\
package bar;
interface A {
    void foo(Foo2 f);
    void foo(Foo1 f);
}
"""
    myFixture.addClass "package bar; class Foo1 {}"
    myFixture.addClass "package bar; class Foo2 {}"
    def file = myFixture.addClass("""\
package bar;
class Test implements A {
    public void foo(Foo1 f) {}
    <caret>
}
""").containingFile.virtualFile
    myFixture.configureFromExistingVirtualFile(file)

    invokeAction(true)

    myFixture.checkResult """\
package bar;
class Test implements A {
    public void foo(Foo1 f) {}

    @Override
    public void foo(Foo2 f) {
        <caret>
    }
}
"""
  }

  void testTypeAnnotationsInImplementedMethod() {
    def handler = new OverrideImplementsAnnotationsHandler() { @Override String[] getAnnotations(Project project) { return ["TA"] } }
    OverrideImplementsAnnotationsHandler.EP_NAME.getPoint().registerExtension(handler, testRootDisposable)

    myFixture.addClass """\
      import java.lang.annotation.*;
      @Target(ElementType.TYPE_USE)
      public @interface TA { }""".stripIndent()

    myFixture.configureByText "test.java", """\
      import java.util.*;

      interface I {
          @TA List<@TA String> i(@TA String p1, @TA(1) int @TA(2) [] p2 @TA(3) []) throws @TA IllegalArgumentException;
      }

      class C implements I {
          <caret>
      }""".stripIndent()

    invokeAction(true)

    myFixture.checkResult """\
      import java.util.*;

      interface I {
          @TA List<@TA String> i(@TA String p1, @TA(1) int @TA(2) [] p2 @TA(3) []) throws @TA IllegalArgumentException;
      }

      class C implements I {
          @Override
          public @TA List<@TA String> i(@TA String p1, @TA(1) int @TA(3) [] @TA(2) [] p2) throws @TA IllegalArgumentException {
              return null;
          }
      }""".stripIndent()
  }

  void testNoCustomOverrideImplementsHandler() {
    myFixture.addClass """package a; public @interface A { }"""

    myFixture.configureByText "test.java", """\
      import java.util.*;
      import a.*;

      interface I {
          @A List<String> i(@A String p);
      }

      class C implements I {
          <caret>
      }""".stripIndent()

    invokeAction(true)

    myFixture.checkResult """\
      import java.util.*;
      import a.*;

      interface I {
          @A List<String> i(@A String p);
      }

      class C implements I {
          @Override
          public List<String> i(String p) {
              return null;
          }
      }""".stripIndent()
  }

  void testCustomOverrideImplementsHandler() throws Exception {
    myFixture.addClass """package a; public @interface A { String value();}"""

    ServiceContainerUtil.registerExtension(ApplicationManager.application, OverrideImplementsAnnotationsHandler.EP_NAME, new OverrideImplementsAnnotationsHandler() {
      @Override
      String[] getAnnotations(Project project) {
        return ["a.A"]
      }
    }, myFixture.getTestRootDisposable())
    myFixture.configureByText "test.java", """\
      import java.util.*;
      import a.*;

      interface I {
          @A("") List<String> i(@A("a") String p);
      }

      class C implements I {
          <caret>
      }""".stripIndent()

    invokeAction(true)

    myFixture.checkResult """\
      import java.util.*;
      import a.*;

      interface I {
          @A("") List<String> i(@A("a") String p);
      }

      class C implements I {
          @A("")
          @Override
          public List<String> i(@A("a") String p) {
              return null;
          }
      }""".stripIndent()
  }

  void "test invocation before orphan type parameters does not lead to stub-AST mismatches"() {
    myFixture.configureByText 'a.java', '''
public class Test implements Runnable{
    int i = ; <caret><X>
}'''
    invokeAction(true)
    PsiTestUtil.checkStubsMatchText(file)
    assert file.text.contains('run()')
  }

  private void doTest(boolean toImplement) {
    String name = getTestName(false)
    myFixture.configureByFile("before${name}.java")
    invokeAction(toImplement)
    myFixture.checkResultByFile("after${name}.java")
  }

  private void invokeAction(boolean toImplement) {
    int offset = myFixture.getEditor().getCaretModel().getOffset()
    PsiClass psiClass = PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), offset, PsiClass.class, false)
    assert psiClass != null
    OverrideImplementUtil.chooseAndOverrideOrImplementMethods(getProject(), myFixture.getEditor(), psiClass, toImplement)
  }
}