// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.completion.command.CommandCompletionDocumentationProvider
import com.intellij.codeInsight.completion.command.CommandCompletionLookupElement
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.documentation.AsyncDocumentation
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.replaceService
import com.siyeh.ig.style.SizeReplaceableByIsEmptyInspection
import kotlinx.coroutines.runBlocking
import javax.swing.JComponent

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class JavaCommandsCompletionTest : LightFixtureCompletionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
  }

  fun testFormatNotCall() {
    Registry.get("ide.completion.command.force.enabled").setValue(false, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          int x =                           y.<caret>;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("format", ignoreCase = true) })
  }

  fun testFormat() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          int x =                           y.<caret>;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("format", ignoreCase = true) })
    myFixture.checkResult("""
      class A { 
        void foo() {
          int y = 10;
            int x = y;
        } 
      }
    """.trimIndent())
  }

  fun testFormatPreview() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          int x =                           y.<caret>;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    val item = elements.first { element -> element.lookupString.contains("format", ignoreCase = true) }
      .`as`(CommandCompletionLookupElement::class.java)
    if (item == null) {
      fail()
      return
    }
    val preview = item.command.getPreview()
    if (preview !is IntentionPreviewInfo.CustomDiff) {
      fail()
      return
    }
    val expected = """
      class A { 
        void foo() {
          int y = 10;
            int x = y;
        } 
      }
    """.trimIndent()
    assertEquals(preview.modifiedText(), expected)
  }

  fun testFormatWholeMethod() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo                     (                             ) {
          int y = 10;
          int x =                           y;
        }.<caret> 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("format", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          void foo() {
              int y = 10;
              int x = y;
          } 
      }
    """.trimIndent())
  }

  fun testEmptyMatchers() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        m<caret> 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertFalse(elements.any { element -> element.lookupString.contains("Generate", ignoreCase = true) })
  }


  fun testUndo() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          int x =                           y.format<caret>;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("format", ignoreCase = true) })
    myFixture.checkResult("""
      class A { 
        void foo() {
          int y = 10;
            int x = y;
        } 
      }
    """.trimIndent())
    val editors = FileEditorManager.getInstance(project).getEditors(myFixture.file.virtualFile)
    UndoManager.getInstance(project).undo(editors[0])
    myFixture.checkResult("""
      class A { 
        void foo() {
          int y = 10;
          int x =                           y.format;
        } 
      }
    """.trimIndent())
  }

  fun testFormatOutside() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10;
          int x =                           y;
        } 
      }
      .<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Reformat", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          void foo() {
              int y = 10;
              int x = y;
          }
      }

    """.trimIndent())
  }

  fun testCopyFqn() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo.<caret>() {
          int y = 10;
        } 
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("copy ref", ignoreCase = true) })
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      class A { 
        void foofoo()() {
          int y = 10;
        } 
      }
    """.trimIndent())
  }

  fun testGenerateConstructor() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        .<caret>
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Generate 'Con", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          public A() {
          }
      }
    """.trimIndent())
  }

  fun testGenerateConstructorWithoutSpecialChars() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        .generate con<caret>
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Generate 'Constructor'", ignoreCase = true) })
  }

  fun testGenerateConstructorInline() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        generate 'con<caret>
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Generate 'Con", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          public A() {
          }
      }
    """.trimIndent())
  }

  fun testOptimizeImport() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import java.util.List;
      .<caret>
      class A {
          void foo() {
              String y = "1";
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Optimize im", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      class A {
          void foo() {
              String y = "1";
          }
      }""".trimIndent())
  }

  fun testOptimizeImport2() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import java.util.List;.<caret>
      
      class A {
          void foo() {
              String y = "1";
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    assertTrue(elements.any { element -> element.lookupString.contains("Optimize im", ignoreCase = true) })
  }

  fun testGenerateGetter() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          String y.<caret>;
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Getter", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          String y;

          public String getY() {
              return y;
          }
      }
      """.trimIndent())
  }

  fun testDeleteLine() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          String y;

          public String getY() {
              return y;.<caret>
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Delete", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          String y;

          public String getY() {
          }
      }""".trimIndent())

  }

  fun testInline() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          public String getY() {
              String y = "y";
              return y.<caret>;
          }
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Inline", ignoreCase = true) })
    myFixture.checkResult("""
      class A {
          public String getY() {
              return "y";
          }
      }
      """.trimIndent())

  }

  fun testInlineMethod() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          public String getY() {
              String y = getX();
              return y;
          }
          
          public String getX.<caret>(){return "1";}
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Inline", ignoreCase = true) })
    myFixture.checkResult("""
        class A {
            public String getY() {
                String y = "1";
                return y;
            }
        
        }""".trimIndent())
  }

  fun testInlineFieldWithNoInitializerNoCommand() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
          public class User {
              String name;
              String some;
          
              public String toString(String a) {
                  return "User{" +
                          "name='" + name..<caret> + '\'' +
                          ", some='" + some + '\'' +
                          '}' + a;
              }
          }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertFalse(elements.any { element -> element.lookupString.contains("Inline", ignoreCase = true) })
  }


  fun testInlineFieldWithInitializer() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
          public class User {
              final String name = "1";
              String some;
          
              public String toString(String a) {
                  return "User{" +
                          "name='" + name..<caret> + '\'' +
                          ", some='" + some + '\'' +
                          '}' + a;
              }
          }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Inline", ignoreCase = true) })
    myFixture.checkResult("""
      public class User {
          String some;
      
          public String toString(String a) {
              return "User{" +
                      "name='" + "1" + '\'' +
                      ", some='" + some + '\'' +
                      '}' + a;
          }
      }
      """.trimIndent())
  }

  fun testInlineParametersNoCommand() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
          public class User {
              String name;
              String some;
          
              public String toString(String a) {
                  return "User{" +
                          "name='" + name + '\'' +
                          ", some='" + some + '\'' +
                          '}' + a..<caret>;
              }
          }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertFalse(elements.any { element -> element.lookupString.contains("Inline", ignoreCase = true) })
  }

  fun testCommentElementByLine() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          public String getY() {
              return "y";
          }
      }.<caret>""".trimIndent())
    val elements = myFixture.completeBasic()
    val item = elements.first { element -> element.lookupString.contains("Comment with line comment", ignoreCase = true) }
    selectItem(item)
    val expectedText = """
      //class A {
      //    public String getY() {
      //        return "y";
      //    }
      //}""".trimIndent()
    myFixture.checkResult(expectedText)
    val lookupElement = item.`as`(CommandCompletionLookupElement::class.java)
    if (lookupElement == null) {
      fail()
      return
    }
    val preview = lookupElement.command.getPreview()
    if (preview !is IntentionPreviewInfo.CustomDiff) {
      fail()
      return
    }
    assertEquals(preview.modifiedText(), expectedText)
  }

  fun testCommentElementByBlock() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          public String getY() {
              return "y";
          }
      }.<caret>""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Comment with block", ignoreCase = true) })
    myFixture.checkResult("""
      /*
      class A {
          public String getY() {
              return "y";
          }
      }*/

      """.trimIndent())
  }

  fun testRename() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo() {
              String y.<caret> = "1";
              System.out.println(y);
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Rename", ignoreCase = true) })
    myFixture.type('\n')
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      class A {
          void foo() {
              String n
              umber = "1";
              System.out.println(number);
          }
      }""".trimIndent())
  }

  fun testRenameMethod1() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo().<caret> {
              String y = "1";
              System.out.println(y);
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    val lookupElement = elements
      .firstOrNull { element -> element.lookupString.contains("rename", ignoreCase = true) }
      ?.`as`(CommandCompletionLookupElement::class.java)
    assertNotNull(lookupElement)
    assertEquals(TextRange(19, 22), (lookupElement as CommandCompletionLookupElement).highlighting?.range)
  }

  fun testRenameMethod2() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo() {
              String y = "1";
              System.out.println(y);
          }.<caret>
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    val lookupElement = elements
      .firstOrNull { element -> element.lookupString.contains("rename", ignoreCase = true) }
      ?.`as`(CommandCompletionLookupElement::class.java)
    assertNotNull(lookupElement)
    assertEquals(TextRange(19, 22), (lookupElement as CommandCompletionLookupElement).highlighting?.range)
  }

  fun testRenameMethod3() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo.<caret>() {
              String y = "1";
              System.out.println(y);
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    val lookupElement = elements
      .firstOrNull { element -> element.lookupString.contains("rename", ignoreCase = true) }
      ?.`as`(CommandCompletionLookupElement::class.java)
    assertNotNull(lookupElement)
    assertEquals(TextRange(19, 22), (lookupElement as CommandCompletionLookupElement).highlighting?.range)
  }

  fun testParameterRename() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo(String a.<caret>) {
              String y = "1";
              System.out.println(y);
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    val element = elements
      .firstOrNull { element -> element.lookupString.contains("Rename", ignoreCase = true) }
      ?.`as`(CommandCompletionLookupElement::class.java)
    assertNotNull(element)
    assertEquals(TextRange(30, 31), element?.highlighting?.range)
  }

  fun testRenameClass() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo() {
              String y = "1";
              System.out.println(y);
          }
      }.<caret>""".trimIndent())
    val elements = myFixture.completeBasic()
    assertTrue(elements.any { element -> element.lookupString.contains("Rename", ignoreCase = true) })
  }

  fun testRenameParameter() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo(String a.<caret>) {
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    assertTrue(elements.any { element -> element.lookupString.contains("Rename", ignoreCase = true) })
  }

  fun testCommandsOnlyGoToDeclaration() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 1;
          int x = y..<caret>;
        }

        class B {}
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Go to dec", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      class A { 
        void foo() {
          int <caret>y = 1;
          int x = y;
        }

        class B {}
      }
    """.trimIndent())
  }

  fun testCommandsOnlyGoToImplementation() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      interface A{

          public void a.<caret>();

          class B implements A{

              @Override
              public void a() {

              }
          }
      }      
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Go to impl", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      interface A{

          public void a();

          class B implements A{

              @Override
              public void <caret>a() {

              }
          }
      }      
      """.trimIndent())
  }

  fun testCommandsOnlyGoToImplementationNotFound() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      interface A{
          public void a.<caret>();
      }      
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertFalse(elements.any { element -> element.lookupString.contains("Go to impl", ignoreCase = true) })
  }

  fun testCommandsGoToSuper() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
        public class TestSuper {
        
            public void foo() {}
            
            public static class Child extends TestSuper {
                @Override
                public void foo().<caret> {
                    super.foo();
                    System.out.println();
                }
            }
        }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Go to super", ignoreCase = true) })
    myFixture.checkResult("""
        public class TestSuper {
        
            public void <caret>foo() {}
            
            public static class Child extends TestSuper {
                @Override
                public void foo() {
                    super.foo();
                    System.out.println();
                }
            }
        }
      """.trimIndent())
  }

  fun testRedCode() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    runBlocking {
      val psiFile = myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          int y = 10L<caret>;
        } 
      }
      """.trimIndent())
      myFixture.doHighlighting()
      myFixture.type(".")
      val elements = myFixture.completeBasic()
      val item = elements.first { element -> element.lookupString.contains("Convert literal to", ignoreCase = true) }
      val documentationProvider = CommandCompletionDocumentationProvider()
      val documentationTarget = documentationProvider.documentationTarget(psiFile, item, editor.caretModel.offset)
      val documentation = documentationTarget?.computeDocumentation() as? AsyncDocumentation
      assertNotNull(documentation)
      val resultDocumentation = documentation?.supplier?.invoke() as? DocumentationData
      assertNotNull(resultDocumentation)
      val expected = "<div style=\"min-width: 150px; max-width: 250px; padding: 0; margin: 0;\"> \n" +
                     "<div style=\"width: 95%; background-color:#ffffff; line-height: 1.3200000524520874\"><div style=\"background-color:#ffffff;color:#000000\"><pre style=\"font-family:'JetBrains Mono',monospace;\"><span style=\"font-size: 90%; color:#999999;\">  2  </span><span style=\"color:#000080;font-weight:bold;\">int&#32;</span>y&#32;=&#32;<span style=\"color:#0000ff;background-color:#cad9fa;\">10</span>;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;&#32;</pre></div><br/>\n" +
                     "</div></div>"
      assertEquals(expected, resultDocumentation?.html ?: "")
      selectItem(item)
      myFixture.checkResult("""
      class A { 
        void foo() {
          int y = 10;
        } 
      }
    """.trimIndent())
    }
  }

  fun testRedCodeImport() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    runBlocking {
      myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          List<String> y = new ArrayList<caret><String>;
        } 
      }
      """.trimIndent())
      myFixture.doHighlighting()
      myFixture.type(".")
      val elements = myFixture.completeBasic()
      assertTrue(elements.any { element -> element.lookupString.contains("Import", ignoreCase = true) })
    }
  }

  fun testChangeSignature() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    var text = """
      abstract class A { 
        void foo() <caret>{
        }<caret>
        abstract void bar<caret>()<caret>;
      }
      """.trimIndent()
    val indexes = mutableListOf<Int>()
    var index = -1
    while (true) {
      index = text.indexOf("<caret>", index + 1)
      if (index == -1) {
        break
      }
      text = text.replaceFirst("<caret>", "")
      indexes.add(index)
    }
    myFixture.configureByText(JavaFileType.INSTANCE, text.replace("<caret>", ""))
    for (index in indexes) {
      myFixture.editor.caretModel.moveToOffset(index)
      myFixture.doHighlighting()
      myFixture.type(".")
      val elements = myFixture.completeBasic()
      assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Change Sign", ignoreCase = true) })
      myFixture.performEditorAction("EditorBackSpace")
    }
  }

  fun testFlipIntention() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
          if(1==2<caret>){}
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    myFixture.type(".")
    val elements = myFixture.completeBasic()
    val item = elements.first { element -> element.lookupString.contains("flip '=='", ignoreCase = true) }
    assertEquals(TextRange(33, 37), (item.`as`(CommandCompletionLookupElement::class.java))?.highlighting?.range)
    selectItem(item)
    myFixture.checkResult("""
      class A { 
        void foo() {
          if(2 == 1){}
        } 
      }
    """.trimIndent())
  }

  fun testUncommentBlockComment() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
        class A {
            /*// Static initialization block
            static {
                System.out.println("Class A is being initialized");
            }*/<caret>
        }
      """.trimIndent())
    myFixture.doHighlighting()
    myFixture.type(".")
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("uncomment", ignoreCase = true) })
    myFixture.checkResult("""
        class A {
            // Static initialization block
            static {
                System.out.println("Class A is being initialized");
            }
        }
    """.trimIndent())
  }

  fun testCreateFromUsages() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Test {

          public static void main(String[] args){
              Test t = new Test()
              t<caret>
          }
      }
      """.trimIndent())
    myFixture.type(".test")
    val elements = myFixture.completeBasic()
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    selectItem(elements.first { element -> element.lookupString.contains("Create method from", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      class Test {
      
          public static void main(String[] args){
              Test t = new Test()
              t.test();
          }
      
          private void test() {
          }
      }
    """.trimIndent())
  }

  fun testCreateFromUsagesSkipConstructors() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Test {

          public static void main(String[] args){
              new Test<caret>()
          }
      }
      """.trimIndent())
    myFixture.type(".")
    val elements = myFixture.completeBasic()
    assertFalse(elements.any { element -> element.lookupString.contains("Create method from", ignoreCase = true) })
  }

  fun testCreateFromUsagesSkipConstructorsDoubleDots() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Test {

          public static void main(String[] args){
              new Test<caret>()
          }
      }
      """.trimIndent())
    myFixture.type("..")
    val elements = myFixture.completeBasic()
    assertFalse(elements.any { element -> element.lookupString.contains("Create method from", ignoreCase = true) })
  }

  fun testInspection() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.enableInspections(SizeReplaceableByIsEmptyInspection())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import java.util.List;
      class A { 
        void foo(List<String> a) {
          if(a.size()==0)<caret>{}
        } 
      }
      """.trimIndent())
    myFixture.doHighlighting()
    myFixture.type(".")
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("isEmpty", ignoreCase = true) })
    myFixture.checkResult("""
      import java.util.List;
      class A { 
        void foo(List<String> a) {
          if(a.isEmpty()){}
        } 
      }
      """.trimIndent())
  }

  fun testBinaryNotAllowed() {
    Registry.get("ide.completion.command.force.enabled").setValue(false, getTestRootDisposable())
    val psiClass = JavaPsiFacade.getInstance(project).findClass(JAVA_LANG_CLASS, GlobalSearchScope.allScope(project))
    val file = psiClass?.containingFile?.virtualFile
    assertNotNull(file)
    myFixture.openFileInEditor(file!!)
    val text = myFixture.file.text
    val pattern = " Serializable"
    val index = text.indexOf(pattern)
    assertTrue(index >= 0)
    myFixture.editor.caretModel.moveToOffset(index + pattern.length)
    val hintManager = createTestHintManager()
    type(".")
    assertTrue(hintManager.called)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    assertNull(lookup)
  }

  fun testBinaryNotAllowedCalledCompletion() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    Registry.get("ide.completion.command.support.read.only.files").setValue(true, getTestRootDisposable())
    val psiClass = JavaPsiFacade.getInstance(project).findClass(JAVA_LANG_CLASS, GlobalSearchScope.allScope(project))
    val file = psiClass?.containingFile?.virtualFile
    assertNotNull(file)
    myFixture.openFileInEditor(file!!)
    val text = myFixture.file.text
    val pattern = " Serializable"
    val index = text.indexOf(pattern)
    assertTrue(index >= 0)
    myFixture.editor.caretModel.moveToOffset(index + pattern.length)
    val hintManager = createTestHintManager()
    type(".")
    assertTrue(!hintManager.called)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    assertNotNull(lookup.items.first { element -> element.lookupString.contains("Copy Refer", ignoreCase = true) })
  }

  private fun createTestHintManager(): TestHintManager {
    val manager = TestHintManager()
    ApplicationManager.getApplication().replaceService(HintManager::class.java, manager, testRootDisposable)
    return manager
  }

  fun testExtractField() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo() {
              String y.<caret> = "1";
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Introduce field", ignoreCase = true) })
    myFixture.type('\n')
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
    class A {

        private String y;
    
        void foo() {
            y = "1";
       
        }
    }""".trimIndent())
  }

  fun testExtractParameter() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo() {
              String y.<caret> = "1";
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Introduce parameter", ignoreCase = true) })
    myFixture.type('\n')
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
    class A {
        void foo(String y) {
        }
    }

    """.trimIndent())
  }

  fun testExtractConstant() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
          void foo() {
              String y = "1".<caret>;
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Introduce constant", ignoreCase = true) })
    myFixture.type('\n')
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
    class A {

        public static final String Y = "1";

        void foo() {
        }
    }

    """.trimIndent())
  }

  fun testMoveMethod() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      public class Main {

          public static String a(String a2) {
              System.out.println(a2);
              return "1";
          }.<caret>

      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertTrue(elements.any { element -> element.lookupString.equals("Move", ignoreCase = true) })
  }

  fun testCopyClass() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      public class Main.<caret> {

          public static String a(String a2) {
              System.out.println(a2);
              return "1";
          }

      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertTrue(elements.any { element -> element.lookupString.equals("Copy", ignoreCase = true) })
  }

  fun testInlineReferenceOnlyVariables() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
        public class B {
            void foo(){
            }
        }

        class C extends B {
            @Override..<caret>
            void foo() {
                super.foo();
            }
        }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertTrue(elements.none { element -> element.lookupString.contains("Inline", ignoreCase = true) })
  }

  fun testShowOnlyStrictWarning() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
        public class B {
          public void someMethod(String s, String string..<caret>) {
          }
        }
      """.trimIndent())
    myFixture.enableInspections(UnusedDeclarationInspection())
    myFixture.doHighlighting()
    val elements = myFixture.completeBasic()
    assertTrue(elements.none { element -> element.lookupString.contains("Rename 's'", ignoreCase = true) })
  }

  fun testDoNotCloseAfterPreview() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Test {
          void f(Iterable<String> it) {
            it.<caret>;
          }
        }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    val lookupElement = elements.first { element -> element.lookupString.contains("Iterate over", ignoreCase = true) }
    assertNotNull(lookupElement)
    val element = lookupElement.`as`(CommandCompletionLookupElement::class.java)
    assertNotNull(element)
    assertNotNull(element?.preview)
    assertNotNull(lookup)
  }

  fun testNoUsualCompletionAfterDoubleDot() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
        class A {
            void method() {
                var l = new String("1"..to<caret>);
            }
        }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertTrue(elements.none { element -> element.lookupString.contains("toString", ignoreCase = true) })
  }

  fun testNoCreateFromUsagesAfterDoubleDot() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
        enum Color {
          RED, GREEN, BLUE, YELLOW, BROWN
        }

        class A {
          Color color = Color.BROWN..<caret>;
        }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Create method from", ignoreCase = true) })
  }

  fun testCreateFromUsagesBeforeSemiComa() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
        enum Color {
          RED, GREEN, BLUE, YELLOW, BROWN
        }

        class A {
          Color color = Color.BROWN.<caret>;
        }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertTrue(elements.any { element -> element.lookupString.contains("Create method from", ignoreCase = true) })
  }

  fun testForceCallException() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A { 
        void foo() {
        } 
      }
      a<caret>""".trimIndent())
    myFixture.completeBasic()
  }


  fun testHighlightingFormat() {
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import java.util.List;
      .<caret>
      class A {
          void foo() {
              String y = "1";
          }
      }""".trimIndent())
    val elements = myFixture.completeBasic()
    val lookupElement = elements.first { element ->
      element.`as`(CommandCompletionLookupElement::class.java) != null &&
      element.lookupString.contains("Reformat", ignoreCase = true)
    }
    val completionLookupElement = lookupElement.`as`(CommandCompletionLookupElement::class.java)
    if (completionLookupElement == null) {
      fail()
      return
    }
    assertEquals(TextRange(0, 82), completionLookupElement.highlighting?.range)
  }

  private class TestHintManager : HintManagerImpl() {
    var called: Boolean = false
    override fun showInformationHint(editor: Editor, component: JComponent, position: Short, onHintHidden: Runnable?) {
      super.showInformationHint(editor, component, position, onHintHidden)
      called = true
    }
  }
}