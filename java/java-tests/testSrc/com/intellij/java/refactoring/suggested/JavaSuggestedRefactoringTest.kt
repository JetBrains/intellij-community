// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle.message
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import com.intellij.refactoring.suggested.SuggestedRefactoringProviderImpl
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import com.intellij.refactoring.suggested._suggestedChangeSignatureNewParameterValuesForTests
import com.intellij.refactoring.suggested.suggestedRefactoringFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaSuggestedRefactoringTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val suggested by suggestedRefactoringFixture(projectFixture)

  @BeforeEach
  fun setUpNewParameterValues() {
    var i = 0
    _suggestedChangeSignatureNewParameterValuesForTests = {
      SuggestedRefactoringExecution.NewParameterValue.Expression(
        PsiElementFactory.getInstance(suggested.project).createExpressionFromText("default${i++}", null)
      )
    }
  }

  @Test
  fun testRenameClass() {
    suggested.doTestRename(
      """
        class C {
            private class Inner<caret> {
            }
            
            private void foo(Inner inner) {
            }
        }
      """.trimIndent(),
      """
        class C {
            private class InnerNew<caret> {
            }
            
            private void foo(InnerNew innerNew) {
            }
        }
      """.trimIndent(),
      "Inner",
      "InnerNew",
      { type("New") }
    )
  }

  @Test
  fun testRenameMethod() {
    suggested.doTestRename(
      """
        class C {
            void foo<caret>(float f) {
                foo(1);
            }
            
            void bar() {
                foo(2);
            }
        }
      """.trimIndent(),
      """
        class C {
            void fooNew<caret>(float f) {
                fooNew(1);
            }
            
            void bar() {
                fooNew(2);
            }
        }
      """.trimIndent(),
      "foo",
      "fooNew",
      { type("New") }
    )
  }

  @Test
  fun testRenameField() {
    suggested.doTestRename(
      """
        class C {
            private int <caret>field;

            public C(int field) {
                this.field = field;
            }    

            void foo() {
                field++;
            }
        }
      """.trimIndent(),
      """
        class C {
            private int newField<caret>;

            public C(int newField) {
                this.newField = newField;
            }    

            void foo() {
                newField++;
            }
        }
      """.trimIndent(),
      "field",
      "newField"
    ) {
      performAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
      type("newField")
    }
  }

  @Test
  fun testRenameLocal() {
    suggested.doTestRename(
      """
        class C {
            void foo(float f) {
                float local<caret> = f;
                local += 1;
            }
        }
      """.trimIndent(),
      """
        class C {
            void foo(float f) {
                float localNew<caret> = f;
                localNew += 1;
            }
        }
      """.trimIndent(),
      "local",
      "localNew",
      { type("New") }
    )
  }

  @Test
  fun testRenameClass2() {
    suggested.doTestRename(
      """
        class C {
            private class <caret>Inner {
            }
            
            private void foo(Inner inner) {
            }
        }
      """.trimIndent(),
      """
        class C {
            private class New<caret>Inner {
            }
            
            private void foo(NewInner newInner) {
            }
        }
      """.trimIndent(),
      "Inner",
      "NewInner",
      { type("New") }
    )
  }

  @Test
  fun testChangeReturnType() {
    suggested.ignoreErrorsAfter = true
    suggested.doTestChangeSignature(
      """
        interface I {
            <caret>void foo(float f);
        }
        
        class C implements I {
            public void foo(float f) {
            }
        }
      """.trimIndent(),
      """
        interface I {
            <caret>int foo(float f);
        }
        
        class C implements I {
            public int foo(float f) {
            }
        }
      """.trimIndent(),
      "implementations",
    ) {
      replaceTextAtCaret("void", "int")
    }
  }

  @Test
  fun testAddParameter() {
    suggested.ignoreErrorsAfter = true
    suggested.doTestChangeSignature(
      """
        package ppp;
        
        import java.io.IOException;
        
        interface I {
            void foo(String s<caret>) throws IOException;
        }
        
        class C implements I {
            public void foo(String s) throws IOException {
            }
        
            void bar(I i) {
                try { 
                   i.foo("");
                } catch(Exception e) {}   
            }
        }

        class C1 implements I {
            public void foo(String s) /* don't add IOException here! */ {
            }
        }
      """.trimIndent(),
      """
        package ppp;

        import java.io.IOException;
        
        interface I {
            void foo(String s, I p<caret>) throws IOException;
        }
        
        class C implements I {
            public void foo(String s, I p) throws IOException {
            }
        
            void bar(I i) {
                try { 
                   i.foo("", default0);
                } catch(Exception e) {}   
            }
        }

        class C1 implements I {
            public void foo(String s, I p) /* don't add IOException here! */ {
            }
        }
      """.trimIndent(),
      "usages",
      expectedPresentation = """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'String'
            ' '
            's'
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          'throws'
          LineBreak(' ', true)
          'IOException'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'String'
            ' '
            's'
          ','
          LineBreak(' ', true)
          Group (added):
            'I'
            ' '
            'p'
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          'throws'
          LineBreak(' ', true)
          'IOException'
      """.trimIndent()
    ) {
      type(", I p")
    }
  }

  @Test
  fun testRemoveParameter() {
    suggested.doTestChangeSignature(
      """
        interface I {
            void foo(float f, int i<caret>);
        }
        
        class C implements I {
            public void foo(float f, int i) {
            }
        
            void bar(I i) {
                i.foo(1, 2);
            }
        }
      """.trimIndent(),
      """
        interface I {
            void foo(float f<caret>);
        }
        
        class C implements I {
            public void foo(float f) {
            }
        
            void bar(I i) {
                i.foo(1);
            }
        }
      """.trimIndent(),
      "usages",
    ) {
      deleteTextBeforeCaret(", int i")
    }
  }

  @Test
  fun testReorderParameters() {
    suggested.doTestChangeSignature(
      """
        interface I {
            void foo(float p1, int p2, boolean p3<caret>);
        }
        
        class C implements I {
            public void foo(float p1, int p2, boolean p3) {
            }
        
            void bar(I i) {
                i.foo(1, 2, false);
            }
        }
      """.trimIndent(),
      """
        interface I {
            void foo(boolean p3, float p1, int p2);
        }
        
        class C implements I {
            public void foo(boolean p3, float p1, int p2) {
            }
        
            void bar(I i) {
                i.foo(false, 1, 2);
            }
        }
      """.trimIndent(),
      "usages",
    ) {
      performAction(IdeActions.MOVE_ELEMENT_LEFT)
      performAction(IdeActions.MOVE_ELEMENT_LEFT)
    }
  }

  @Test
  fun testChangeParameterType() {
    suggested.doTestChangeSignature(
      """
        interface I {
            void foo(float<caret> f);
        }
        
        class C implements I {
            public void foo(float f) {
            }
        
            void bar(I i) {
                i.foo(1);
            }
        }
      """.trimIndent(),
      """
        interface I {
            void foo(int<caret> f);
        }
        
        class C implements I {
            public void foo(int f) {
            }
        
            void bar(I i) {
                i.foo(1);
            }
        }
      """.trimIndent(),
      "implementations",
    ) {
      repeat("float".length) { performAction(IdeActions.ACTION_EDITOR_BACKSPACE) }
      type("int")
    }

  }

  @Test
  fun testChangeParameterTypeWithImportInsertion() {
    suggested.fixture.addFileToProject(
      "X.java",
      """
        package xxx;
        
        public class X {}
      """.trimIndent()
    )

    val otherFile = suggested.fixture.addFileToProject(
      "Other.java",
      """
        class D implements I {
            public void foo(float p) {
            }
        }
      """.trimIndent()
    )

    suggested.doTestChangeSignature(
      """
        interface I {
            void foo(<caret>float p);
        }
        
        class C implements I {
            public void foo(float p) {
            }
        }
      """.trimIndent(),
      """
        import xxx.X;
        
        interface I {
            void foo(<caret>X p);
        }
        
        class C implements I {
            public void foo(X p) {
            }
        }
      """.trimIndent(),
      "implementations",
    ) {
      replaceTextAtCaret("float", "X")
      addImport("xxx.X")
    }


    assertEquals(
      """
        import xxx.X;
        
        class D implements I {
            public void foo(X p) {
            }
        }
      """.trimIndent(),
      runReadActionBlocking { otherFile.text }
    )
  }

  @Test
  fun testChangeParameterTypeWithImportReplaced() {
    suggested.fixture.addFileToProject(
      "X.java",
      """
        package xxx;
        public class X {}
      """.trimIndent()
    )

    suggested.fixture.addFileToProject(
      "Y.java",
      """
        package xxx; 
        public class Y {}
      """.trimIndent()
    )

    val otherFile = suggested.fixture.addFileToProject(
      "Other.java",
      """
        import xxx.X;
        
        class D implements I {
            public void foo(X p) {
            }
        }
      """.trimIndent()
    )

    suggested.doTestChangeSignature(
      """
        import xxx.X;

        interface I {
            void foo(<caret>X p);
        }
      """.trimIndent(),
      """
        import xxx.Y;
        
        interface I {
            void foo(<caret>Y p);
        }
      """.trimIndent(),
      "implementations",
    ) {
      editAction {
        val offset = editor.caretModel.offset
        editor.document.replaceString(offset, offset + "X".length, "Y")
      }
      addImport("xxx.Y")
      removeImport("xxx.X")
    }


    assertEquals(
      """
        import xxx.Y;
        
        class D implements I {
            public void foo(Y p) {
            }
        }
      """.trimIndent(),
      runReadActionBlocking { otherFile.text }
    )
  }

  @Test
  fun testAddConstructorParameter() {
    suggested.ignoreErrorsAfter = true
    suggested.doTestChangeSignature(
      """
        class C {
            C(float f<caret>) {
            }

            void bar() {
                C c = new C(1);
            }
        }
      """.trimIndent(),
      """
        class C {
            C(float f, int p<caret>) {
            }

            void bar() {
                C c = new C(1, default0);
            }
        }
      """.trimIndent(),
      "usages",
    ) {
      type(", int p")
    }
  }

  @Test
  fun testAddParameterWithAnnotations() {
    addFileWithAnnotations()

    val otherFile = suggested.fixture.addFileToProject(
      "Other.java",
      """
        class C implements I {
            @Override
            public void foo() {
            }
        }
      """.trimIndent()
    )

    suggested.doTestChangeSignature(
      """
        interface I {
            void foo(<caret>);
        }
      """.trimIndent(),
      """
        import annotations.Language;
        import annotations.NonStandard;
        import annotations.NotNull;
        import org.jetbrains.annotations.Nls;
        
        interface I {
            void foo(@NotNull @Language("JAVA") @Nls @NonStandard("X") String s<caret>);
        }
      """.trimIndent(),
      "usages",
      expectedPresentation = """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (added):
            '@NotNull @Nls @NonStandard("X")'
            ' '
            'String'
            ' '
            's'
          LineBreak('', false)
          ')'
      """.trimIndent()
    ) {
      type("@NotNull")
      addImport("annotations.NotNull")
      type(" @Language(\"JAVA\") @Nls @NonStandard(\"X\")")
      addImport("annotations.Language")
      addImport("org.jetbrains.annotations.Nls")
      addImport("annotations.NonStandard")
      type(" String s")
    }

    assertEquals(
      """
        import annotations.NonStandard;
        import annotations.NotNull;
        import org.jetbrains.annotations.Nls;
        
        class C implements I {
            @Override
            public void foo(@NotNull @Nls @NonStandard("X") String s) {
            }
        }
      """.trimIndent(),
      runReadActionBlocking { otherFile.text }
    )
  }

  @Test
  fun testChangeNullableToNotNull() {
    addFileWithAnnotations()

    val otherFile = suggested.fixture.addFileToProject(
      "Other.java",
      """
        import annotations.Nullable;
        
        class C implements I {
            @Override
            public void foo(@Nullable Object o) {
            }
        }
      """.trimIndent()
    )

    suggested.doTestChangeSignature(
      """
        import annotations.Nullable;

        interface I {
            void foo(@Nullable<caret> Object o);
        }
      """.trimIndent(),
      """
        import annotations.NotNull;
        
        interface I {
            void foo(@NotNull<caret> Object o);
        }
      """.trimIndent(),
      "implementations",
      expectedPresentation = """
                Old:
                  'void'
                  ' '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    '@Nullable' (modified)
                    ' '
                    'Object'
                    ' '
                    'o'
                  LineBreak('', false)
                  ')'
                New:
                  'void'
                  ' '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    '@NotNull' (modified)
                    ' '
                    'Object'
                    ' '
                    'o'
                  LineBreak('', false)
                  ')'
              """.trimIndent()
    ) {
      editAction {
        val offset = editor.caretModel.offset
        editor.document.replaceString(offset - "Nullable".length, offset, "NotNull")
      }
      addImport("annotations.NotNull")
      removeImport("annotations.Nullable")
    }

    assertEquals(
      """
            import annotations.NotNull;
            
            class C implements I {
                @Override
                public void foo(@NotNull Object o) {
                }
            }
            """.trimIndent(),
      runReadActionBlocking { otherFile.text }
    )
  }

  // TODO: see IDEA-230807
  @Test
  fun testNotNullAnnotation() {
    addFileWithAnnotations()

    suggested.doTestChangeSignature(
      """
        interface I {<caret>
            Object foo();
        }
        
        class C implements I {
            @Override
            public Object foo() {
                return ""; 
            }
        }
      """.trimIndent(),
      """
        import annotations.NotNull;
        
        interface I {
            @NotNull<caret>
            Object foo();
        }
        
        class C implements I {
            @Override
            public @NotNull Object foo() {
                return ""; 
            }
        }
      """.trimIndent(),
      "implementations",
    ) {
      performAction(IdeActions.ACTION_EDITOR_ENTER)
      type("@NotNull")
      addImport("annotations.NotNull")
    }
  }

  @Test
  fun testAddThrowsList() {
    val otherFile = suggested.fixture.addFileToProject(
      "Other.java",
      """
        class C implements I {
            @Override
            public void foo() {
            }
        }
      """.trimIndent()
    )

    suggested.doTestChangeSignature(
      """
        interface I {
            void foo()<caret>;
        }
      """.trimIndent(),
      """
        import java.io.IOException;
        
        interface I {
            void foo() throws IOException<caret>;
        }
      """.trimIndent(),
      "implementations",
      expectedPresentation = """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          Group (added):
            'throws'
            LineBreak(' ', true)
            'IOException'
      """.trimIndent()
    ) {
      type(" throws IOException")
      addImport("java.io.IOException")
    }

    assertEquals(
      """
        import java.io.IOException;
        
        class C implements I {
            @Override
            public void foo() throws IOException {
            }
        }
      """.trimIndent(),
      runReadActionBlocking { otherFile.text }
    )
  }

  @Test
  fun testRemoveThrowsList() {
    suggested.doTestChangeSignature(
      """
        import java.io.IOException;
        
        interface I {
            void foo() throws IOException<caret>;
        }

        class C implements I {
            @Override
            public void foo() throws IOException {
            }
        }
      """.trimIndent(),
      """
        interface I {
            void foo()<caret>;
        }

        class C implements I {
            @Override
            public void foo() {
            }
        }
      """.trimIndent(),
      "implementations",
      expectedPresentation = """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          Group (removed):
            'throws'
            LineBreak(' ', true)
            'IOException'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
      """.trimIndent()
    ) {
      deleteTextBeforeCaret(" throws IOException")
    }
  }

  @Test
  fun testAddSecondException() {
    suggested.doTestChangeSignature(
      """
        import java.io.IOException;
        
        interface I {
            void foo() throws IOException<caret>;
        }

        class C implements I {
            @Override
            public void foo() throws IOException {
            }
        }
      """.trimIndent(),
      """
        import java.io.IOException;
        
        interface I {
            void foo() throws IOException, NumberFormatException<caret>;
        }

        class C implements I {
            @Override
            public void foo() throws IOException, NumberFormatException {
            }
        }
      """.trimIndent(),
      "implementations",
      expectedPresentation = """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          'throws'
          LineBreak(' ', true)
          'IOException'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          'throws'
          LineBreak(' ', true)
          'IOException'
          ','
          LineBreak(' ', true)
          'NumberFormatException' (added)
      """.trimIndent()
    ) {
      type(", NumberFormatException")
    }
  }

  @Test
  fun testFromUsageSimple() {
    Registry.get("ide.java.refactoring.suggested.call.site").setValue(true, suggested.fixture.testRootDisposable)
    suggested.doTestChangeSignature(
      """
        class X {
          void test(int a, int b) {}
          
          void call() {
            test(1, <caret>2);
          }
        }
      """.trimIndent(),
      """
        class X {
          void test(int a, int i, int b) {}
          
          void call() {
            test(1, 5,2);
          }
        }
      """.trimIndent(),
      "declaration",
      expectedPresentation = """
        Old:
          'void'
          ' '
          'test'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'a'
          ','
          LineBreak(' ', true)
          Group:
            'int'
            ' '
            'b'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'test'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'a'
          ','
          LineBreak(' ', true)
          Group (added):
            'int'
            ' '
            'i'
          ','
          LineBreak(' ', true)
          Group:
            'int'
            ' '
            'b'
          LineBreak('', false)
          ')'
      """.trimIndent()
    ) {
      type("5,")
    }
  }

  @Test
  fun testFromUsageHasOtherUsages() {
    Registry.get("ide.java.refactoring.suggested.call.site").setValue(true, suggested.fixture.testRootDisposable)
    _suggestedChangeSignatureNewParameterValuesForTests = null
    suggested.doTestChangeSignature(
      """
        class X {
          void test(int a, int b) {}
          
          void call() {
            test(1, <caret>2);
          }
          
          void another() {
            String s = "string";
            test(3, 4);
          }
        }
      """.trimIndent(),
      """
        class X {
          void test(int a, int i, String hello, int b) {}
          
          void call() {
            test(1, 5, "hello",2);
          }
          
          void another() {
            String s = "string";
            test(3, 5, s, 4);
          }
        }
      """.trimIndent(),
      "declaration",
      expectedPresentation = """
        Old:
          'void'
          ' '
          'test'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'a'
          ','
          LineBreak(' ', true)
          Group:
            'int'
            ' '
            'b'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'test'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'a'
          ','
          LineBreak(' ', true)
          Group (added):
            'int'
            ' '
            'i'
          ','
          LineBreak(' ', true)
          Group (added):
            'String'
            ' '
            'hello'
          ','
          LineBreak(' ', true)
          Group:
            'int'
            ' '
            'b'
          LineBreak('', false)
          ')'
      """.trimIndent()
    ) {
      type("5, \"hello\",")
    }
  }

  @Test
  fun testChangeVisibility1() {
    //TODO: see IDEA-230741
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts<Throwable> {
      suggested.doTestChangeSignature(
        """
          abstract class Base {
              <caret>protected abstract void foo();
          }
          
          class C extends Base {
              @Override
              protected void foo() {
              }
          }
        """.trimIndent(),
        """
          abstract class Base {
              <caret>public abstract void foo();
          }
          
          class C extends Base {
              @Override
              public void foo() {
              }
          }
        """.trimIndent(),
        "implementations",
        expectedPresentation = """
          Old:
            'protected' (modified)
            ' '
            'void'
            ' '
            'foo'
            '('
            LineBreak('', false)
            ')'
          New:
            'public' (modified)
            ' '
            'void'
            ' '
            'foo'
            '('
            LineBreak('', false)
            ')'
        """.trimIndent()
      ) {
        editAction {
          val offset = editor.caretModel.offset
          editor.document.replaceString(offset, offset + "protected".length, "public")
        }
      }
    }
  }

  @Test
  fun testChangeVisibility2() {
    //TODO: see IDEA-230741
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts<Throwable> {
      suggested.doTestChangeSignature(
        """
          abstract class Base {
              <caret>abstract void foo();
          }
          
          class C extends Base {
              @Override
              void foo() {
              }
          }
        """.trimIndent(),
        """
          abstract class Base {
              public <caret>abstract void foo();
          }
          
          class C extends Base {
              @Override
              public void foo() {
              }
          }
        """.trimIndent(),
        "implementations",
        expectedPresentation = """
          Old:
            'void'
            ' '
            'foo'
            '('
            LineBreak('', false)
            ')'
          New:
            'public' (added)
            ' '
            'void'
            ' '
            'foo'
            '('
            LineBreak('', false)
            ')'
        """.trimIndent()
      ) {
        type("public ")
      }
    }
  }

  @Test
  fun testUndoRename() {
    suggested.ignoreErrorsAfter = true
    suggested.doTestRename(
      """
        class C {
            void xxx<caret>() {
            }
            
            void yyy() {
                xxx();
            }    
        }
      """.trimIndent(),
      """
        class C {
            void xxx1<caret>() {
            }
            
            void yyy() {
                xxx1();
            }    
        }
      """.trimIndent(),
      "xxx",
      "xxx1",
    ) {
      executeCommand(project) { type("1") }
      executeCommand(project) { suggestedRefactoringIntention()!!.invoke(project, editor, file) }
      performAction(IdeActions.ACTION_UNDO)
    }
  }

  @Test
  fun testUndoChangeSignature() {
    suggested.ignoreErrorsAfter = true
    suggested.doTestChangeSignature(
      """
        class C {
            void xxx(<caret>) {
            }
            
            void yyy() {
                xxx();
            }    
        }
      """.trimIndent(),
      """
        class C {
            void xxx(int p<caret>) {
            }
            
            void yyy() {
                xxx(default1);
            }    
        }
      """.trimIndent(),
      "usages",
    ) {
      executeCommand(project) { type("int p") }
      executeCommand(project) { suggestedRefactoringIntention()!!.invoke(project, editor, file) }
      performAction(IdeActions.ACTION_UNDO)
    }
  }

  @Test
  fun testUndoChangeSignature2() {
    suggested.ignoreErrorsAfter = true
    suggested.doTestChangeSignature(
      """
        class C {
            void xxx(<caret>) {
            }
            
            void yyy() {
                xxx();
            }    
        }
      """.trimIndent(),
      """
        class C {
            void xxx(int p1, int p2<caret>) {
            }
            
            void yyy() {
                xxx(default1, default2);
            }    
        }
      """.trimIndent(),
      "usages",
    ) {
      executeCommand(project) { type("int p1") }
      executeCommand(project) { suggestedRefactoringIntention()!!.invoke(project, editor, file) }
      performAction(IdeActions.ACTION_UNDO)
      executeCommand(project) { type(", int p2") }
    }
  }

  @Test
  fun testBrokenParameters() {
    suggested.doTest(
      initialText = """
          interface I {
              void foo(<caret>);
          }
        """.trimIndent(),
      actionName = message("suggested.refactoring.change.signature.intention.text", "usages"),
      textAfterRefactoring = """
          interface I {
              void foo(String s<caret>);
          }
        """.trimIndent(),
      checkPresentation = {
        var state = SuggestedRefactoringProviderImpl.getInstance(this.project).state!!
        val oldSignature = state.oldSignature
        state = state.withOldSignature(Signature.create(oldSignature.name, oldSignature.type,
                                                oldSignature.parameters.let {
                                                  val list = it.toMutableList()
                                                  list.add(Parameter("s", "s", "String"))
                                                  list
                                                },
                                                oldSignature.additionalData)!!)
        state = state.refactoringSupport.availability.refineSignaturesWithResolve(state)
        val refactoringSupport = state.refactoringSupport
        val refactoring = refactoringSupport.availability.detectAvailableRefactoring(state)
        assertNull(refactoring)
      },
      editingActions = {
        type("String s")
      }
    )
  }

  private fun addFileWithAnnotations() {
    suggested.fixture.addFileToProject(
      "Annotations.java",
      """
        package annotations;
        
        import java.lang.annotation.*;
        
        @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
        public @interface NotNull {}

        @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
        public @interface Nullable {}
        
        @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.ANNOTATION_TYPE})
        public @interface Language {
            String value();
        }
        
        @Target({ElementType.TYPE_USE})
        public @interface NonStandard {
            String value();
        }
      """.trimIndent()
    )
  }

  private fun addImport(fqName: String) = suggested.editAction {
    val psiClass = JavaPsiFacade.getInstance(suggested.project).findClass(fqName, GlobalSearchScope.allScope(suggested.project))!!
    val importStatement = PsiElementFactory.getInstance(suggested.project).createImportStatement(psiClass)
    (suggested.file as PsiJavaFile).importList!!.add(importStatement)
  }

  private fun removeImport(fqName: String) = suggested.editAction {
    (suggested.file as PsiJavaFile).importList!!.findSingleClassImportStatement(fqName)!!.delete()
  }
}
