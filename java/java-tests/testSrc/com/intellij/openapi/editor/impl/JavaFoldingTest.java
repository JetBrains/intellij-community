// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.codeInsight.folding.impl.JavaFoldingBuilder;
import com.intellij.find.FindManager;
import com.intellij.java.codeInsight.folding.JavaFoldingTestCase;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class JavaFoldingTest extends JavaFoldingTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  public void testEndOfLineComments() { doTest(); }

  public void testMultilineComments() { doTest(); }

  public void testJavadocComments() { doTest(); }

  public void testJavadocMarkdownComments() { doTest(); }

  public void testEditingImports() {
    configure("""
                import java.util.List;
                import java.util.Map;
                <caret>

                class Foo { List a; Map b; }
                """);

    assertNotNull(myFixture.getEditor().getFoldingModel().getCollapsedRegionAtOffset(10));

    myFixture.type("import ");
    myFixture.doHighlighting();
    assertNull(myFixture.getEditor().getFoldingModel().getCollapsedRegionAtOffset(46));
  }

  public void testJavadocLikeClassHeader() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      /**
       * This is a header to collapse
       */
      import java.util.*;
      class Foo { List a; Map b; }
                              """;
    configure(text);
    FoldRegion foldRegion = myFixture.getEditor().getFoldingModel().getCollapsedRegionAtOffset(0);
    assertNotNull(foldRegion);
    assertEquals(0, foldRegion.getStartOffset());
    assertEquals(text.indexOf("import") - 1, foldRegion.getEndOffset());
  }

  public void testSubsequentCollapseBlock() {
    String text = """
      class Test {
          void test(int i) {
              if (i > 1) {
                  <caret>i++;
              }
          }
      }
      """;
    configure(text);
    myFixture.performEditorAction("CollapseBlock");
    myFixture.performEditorAction("CollapseBlock");
    assertEquals(text.indexOf("}", text.indexOf("i++")), myFixture.getEditor().getCaretModel().getOffset());
  }

  public void testExpandCollapseRegionTogglesFold() {
    String text = """
      class Test {
          void test(int i) {
              if (i > 1) {
                  <caret>i++;
              }
          }
      }
      """;
    configure(text);
    assertEquals(2, getExpandedFoldRegionsCount());

    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_COLLAPSE_TOGGLE_REGION);
    assertEquals(1, getExpandedFoldRegionsCount());

    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_COLLAPSE_TOGGLE_REGION);
    assertEquals(2, getExpandedFoldRegionsCount());
  }

  public void testFoldGroup() {
    // Implied by IDEA-79420
    myFoldingSettings.setCollapseLambdas(true);
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Test {
          void test() {
              new Runnable() {
                  public void run() {
                      int i = 1;
                  }
              }.run();
          }
      }
      """;

    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
    final FoldRegion closureStartFold = foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable"));
    assertNotNull(closureStartFold);
    assertFalse(closureStartFold.isExpanded());

    assertNotNull(closureStartFold.getGroup());
    List<FoldRegion> closureFolds = foldingModel.getGroupedRegions(closureStartFold.getGroup());
    assertNotNull(closureFolds);
    assertEquals(2, closureFolds.size());

    FoldRegion closureEndFold = closureFolds.get(1);
    assertFalse(closureEndFold.isExpanded());

    myFixture.getEditor().getCaretModel().moveToOffset(closureEndFold.getStartOffset() + 1);
    assertTrue(closureStartFold.isExpanded());
    assertTrue(closureEndFold.isExpanded());

    changeFoldRegions(() -> collapse(closureStartFold));
    assertTrue(closureStartFold.isExpanded());
    assertTrue(closureEndFold.isExpanded());
  }

  public void test_closure_folding_when_an_abstract_method_is_not_in_the_direct_superclass() {
    myFoldingSettings.setCollapseLambdas(true);
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      public abstract class AroundTemplateMethod<T> {
        public abstract T execute();
      }
      abstract class SetupTimer<T> extends AroundTemplateMethod<T> {
      }
      class Test {
          void test() {
           new SetupTimer<Integer>() {
            public Integer execute() {
              return 0;
            }
          };
        }
      }""";

    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
    FoldRegion closureStartFold = foldingModel.getCollapsedRegionAtOffset(text.indexOf("<Integer>"));
    assertNotNull(closureStartFold);
    assertFalse(closureStartFold.isExpanded());

    assertNotNull(closureStartFold.getGroup());
    List<FoldRegion> closureFolds = foldingModel.getGroupedRegions(closureStartFold.getGroup());
    assertNotNull(closureFolds);
    assertEquals(2, closureFolds.size());
  }

  public void test_builder_style_setter() {
    myFoldingSettings.setCollapseAccessors(true);
    @Language("JAVA") String text = """
      class Foo {
          private String bar;

          public Foo setBar(String bar) {
              this.bar = bar;
              return this;
          }
      }""";

    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
    int indexOfBar = text.indexOf("this.bar");
    FoldRegion accessorStartFold = foldingModel.getCollapsedRegionAtOffset(indexOfBar);
    assertNotNull(accessorStartFold);
    assertFalse(accessorStartFold.isExpanded());
  }

  public void test_closure_folding_doesn_t_expand_when_editing_inside() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Test {
          void test() {
           new Runnable() {
            static final long serialVersionUID = 42L;
            public void run() {
              System.out.println();
            }
          };
        }
      }
      """;

    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
    FoldRegion closureStartFold = foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable"));
    assertNotNull(closureStartFold);
    assertFalse(closureStartFold.isExpanded());
    assertTrue(text.substring(closureStartFold.getEndOffset()).startsWith("System"));//one line closure

    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("();") + 1);
    myFixture.type("2");
    myFixture.doHighlighting();
    closureStartFold = foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable"));
    assertNotNull(closureStartFold);
  }

  public void test_closure_folding_placeholder_texts() {
    myFixture.addClass("interface Runnable2 { void run(); }");
    myFixture.addClass("interface Runnable3 { void run(); }");
    myFixture.addClass("interface Runnable4 { void run(); }");
    myFixture.addClass("abstract class MyAction { public abstract void run(); public void registerVeryCustomShortcutSet() {} }");
    myFixture.addClass("abstract class MyAction2 { public abstract void run(); public void registerVeryCustomShortcutSet() {} }");
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Test {
        MyAction2 action2;

        void test() {
          Runnable r = new Runnable() {
            public void run() {
              System.out.println();
            }
          };
          new Runnable2() {
            public void run() {
              System.out.println();
            }
          }.run();
          foo(new Runnable3() {
            public void run() {
              System.out.println();
            }
          });
          bar(new Runnable4() {
            public void run() {
              System.out.println();
            }
          });
          new MyAction() {
            public void run() {
              System.out.println();
            }
          }.registerVeryCustomShortcutSet();
          action2 = new MyAction2() {
            public void run() {
              System.out.println();
            }
          };
        }

        void foo(Object o) { }

        void bar(Runnable4 o) { }
      }
      """;
    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();

    assertEquals("() " + rightArrow() + " { ", 
                 foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable(")).getPlaceholderText());
    assertEquals("(Runnable2) () " + rightArrow() + " { ",
                 foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable2(")).getPlaceholderText());
    assertEquals("(Runnable3) () " + rightArrow() + " { ",
                 foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable3(")).getPlaceholderText());
    assertEquals("() " + rightArrow() + " { ", 
                 foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable4(")).getPlaceholderText());
    assertEquals("(MyAction) () " + rightArrow() + " { ",
                 foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction(")).getPlaceholderText());
    assertEquals("(MyAction2) () " + rightArrow() + " { ",
                 foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction2(")).getPlaceholderText());
  }

  private static String rightArrow() {
    return JavaFoldingBuilder.getRightArrow();
  }

  public void test_closure_folding_after_paste() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Test {
      <caret>// comment
        void test() {
          Runnable r = new Runnable() {
            public void run() {
              System.out.println();
            }
          };
        }
      }
      """;
    configure(text);
    myFixture.performEditorAction("EditorCut");
    myFixture.performEditorAction("EditorPaste");

    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();

    assertEquals(foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable(")).getPlaceholderText(), "() " + rightArrow() + " { ");
  }

  public void test_closure_folding_when_overriding_one_method_of_many() {
    myFixture.addClass("abstract class Runnable { void run() {} void run2() {} }");
    myFixture.addClass("abstract class Runnable2 { void run() {} void run2() {} }");
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Test {
        void test() {
          Runnable r = new Runnable() {
            public void run() {
              System.out.println();
            }
          };
          foo(new Runnable2() {
            public void run2() {
              System.out.println();
            }
          });
        }

        void foo(Object o) { }
      }
      """;
    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();

    final FoldRegion offset = foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable("));
    assertEquals((offset == null ? null : offset.getPlaceholderText()), "run() " + rightArrow() + " { ");
    final FoldRegion offset1 = foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable2("));
    assertEquals((offset1 == null ? null : offset1.getPlaceholderText()), "(Runnable2) run2() " + rightArrow() + " { ");
  }

  public void test_no_closure_folding_when_the_method_throws_an_unresolved_exception() {
    String text = """
      class Test {
          void test() { new Runnable() {
            public void run() throws Asadfsdafdfasd {
              System.out.println(<caret>);
            }
          };
        }
      }""";

    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
    assertNull(foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable")));
  }

  public void test_no_closure_folding_for_synchronized_methods() {
    String text = """
      class Test {
        void test() { new Runnable() {
          public synchronized void run() {
            System.out.println(<caret>);
          }
        };""";
    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
    assertNull(foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable")));
  }

  public void testFindInFolding() {
    String text = """
      class Test {
          void test1() {
          }
          void test2() {
              <caret>test1();
          }
      }
      """;
    configure(text);
    myFixture.performEditorAction("CollapseBlock");
    myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("test1"));
    myFixture.performEditorAction("HighlightUsagesInFile");
    FindManager.getInstance(getProject()).findNextUsageInEditor(myFixture.getEditor());
    assertEquals("test1", myFixture.getEditor().getSelectionModel().getSelectedText());
  }

  public void testCustomFolding() { doTest(); }

  public void testCustomFoldingIdea208441() { doTest(); }

  public void testEmptyMethod() { doTest(); }

  private void doTest() {
    myFixture.testFolding(PathManagerEx.getTestDataPath() + "/codeInsight/folding/" + getTestName(false) + ".java");
  }

  public void test_custom_folding_IDEA_122715_and_IDEA_87312() {
    @Language("JAVA") String text = """
      public class Test {
      
          //region Foo
          interface Foo {void bar();}
          //endregion
      
          //region Bar
          void test() {
      
          }
          //endregion
          enum Bar {
              BAR1,
              BAR2
          }
      }""";
    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
    int count = 0;
    for (FoldRegion region : foldingModel.getAllFoldRegions()) {
      if (region.getStartOffset() == text.indexOf("//region Foo")) {
        assertEquals("Foo", region.getPlaceholderText());
        count++;
      }
      else if (region.getStartOffset() == text.indexOf("//region Bar")) {
        assertEquals("Bar", region.getPlaceholderText());
        count++;
      }
    }
    assertEquals("Not all custom regions are found", 2, count);
  }

  public void test_custom_foldings_intersecting_with_language_ones() {
    @Language("JAVA") String text = """
      class Foo {
      //*********************************************
      // region Some
      //*********************************************

        int t = 1;

      //*********************************************
      // endregion
      //*********************************************
      }
      """;
    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
    assertEquals(1, getFoldRegionsCount());
    assertEquals("Some", foldingModel.getAllFoldRegions()[0].getPlaceholderText());
  }

  public void test_custom_foldings_have_preference() {
    @Language("JAVA") String text = """
      class A {
          // region Some
          @SuppressWarnings("")
          // endregion
          @Deprecated
          void m() {}
      }
      """;
    configure(text);
    assertTrue(Arrays.toString(myFixture.getEditor().getFoldingModel().getAllFoldRegions()).contains("Some"));
  }

  public void test_custom_foldings_intersecting_with_comment_foldings() {
    @Language("JAVA") String text = """
      class Foo {
      // 0
      // 1
      // region Some
      // 2
      // 3 next empty line is significant

      // non-dangling
        int t = 1;
      // 4
      // 5
      // endregion
      // 6
      // 7
      }
      """;
    configure(text);

    assertFolding("// region");
    assertFolding("// 0");
    assertFolding("// 2");// Note: spans only two lines, see next test for details
    assertFolding("// 4");
    assertFolding("// 6");

    assertEquals(5, getFoldRegionsCount());
  }

  public void test_single_line_comments_foldings() {
    @Language("JAVA") String text = """
      class Foo {
      // 0
      // 1
      // 2 next empty line is significant

      // 3
      // 4
        int t = 1;
      // 5
      // 6
      // 7
      }
      """;
    configure(text);

    assertFolding("// 0");
    assertFolding("// 3");
    assertFolding("// 5");

    assertEquals(3, getFoldRegionsCount());
  }

  public void test_custom_folding_collapsed_by_default() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Test {
        void test() {
          //<editor-fold desc="Custom region">
          System.out.println(1);
          System.out.println(2);
          //</editor-fold>
          System.out.println(3);
        };
      }
      """;
    boolean oldValue = CodeFoldingSettings.getInstance().COLLAPSE_CUSTOM_FOLDING_REGIONS;
    try {
      CodeFoldingSettings.getInstance().COLLAPSE_CUSTOM_FOLDING_REGIONS = true;
      configure(text);
      FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
      assertNotNull(foldingModel.getCollapsedRegionAtOffset(text.indexOf("//<editor-fold")));
    }
    finally {
      CodeFoldingSettings.getInstance().COLLAPSE_CUSTOM_FOLDING_REGIONS = oldValue;
    }
  }

  public void test_move_methods() {
    @Language("JAVA") String initialText = """
      class Test {
        void test1() {
        }

        void test2() {
        }
      }
      """;

    Function<String, FoldRegion> fold = methodName -> {
      String text = myFixture.getEditor().getDocument().getText();
      int nameIndex = text.indexOf(methodName);
      int start = text.indexOf("{", nameIndex);
      int end = text.indexOf("}", start) + 1;
      FoldRegion[] regions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
      for (FoldRegion region : regions) {
        if (region.getStartOffset() == start && region.getEndOffset() == end) {
          return region;
        }
      }
      fail("Can't find target fold region for method with name '" + methodName + "'. Registered regions: " + Arrays.toString(regions));
      return null;
    };

    configure(initialText);
    FoldingModelEx foldingModel = (FoldingModelEx)myFixture.getEditor().getFoldingModel();
    foldingModel.runBatchFoldingOperation(() -> {
      fold.apply("test1").setExpanded(true);
      collapse(fold.apply("test2"));
    });

    myFixture.getEditor().getCaretModel().moveToOffset(initialText.indexOf("void"));
    myFixture.performEditorAction("MoveStatementDown");
    CodeFoldingManager.getInstance(getProject()).updateFoldRegions(myFixture.getEditor());
    assertTrue(fold.apply("test1").isExpanded());
    assertFalse(fold.apply("test2").isExpanded());

    myFixture.performEditorAction("MoveStatementUp");
    CodeFoldingManager.getInstance(getProject()).updateFoldRegions(myFixture.getEditor());
    assertTrue(fold.apply("test1").isExpanded());
    assertFalse(fold.apply("test2").isExpanded());
  }

  public void testUnorderedFoldRegionsRegistration() {
    String text = "01234567";
    configure(text);
    final FoldingModelImpl foldModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();
    foldModel.runBatchFoldingOperation(() -> {
      FoldRegion innerFold = foldModel.addFoldRegion(3, 5, "...");
      FoldRegion outerFold = foldModel.addFoldRegion(2, 6, "...");
      innerFold.setExpanded(false);
      collapse(outerFold);
    });
    FoldRegion[] folds = foldModel.fetchVisible();
    assertEquals(1, folds.length);
    assertEquals(2, folds[0].getStartOffset());
    assertEquals(6, folds[0].getEndOffset());
  }

  public void test_simple_property_accessors_in_one_line() {
    @Language("JAVA") String text = """
      class Foo {
       int field;
       int field2;
       int field3;
      
       int getField()
       {
         return field;
       }
      
       void setField(int f) {
         field = f;
       }
      
       void setField2(int f){field2=f;} // normal method folding here
      
        // normal method folding here
       void setField3(int f){
      
         field2=f;
       }
      
      }""";
    configure(text);
    PsiClass fooClass = JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject()));
    FoldRegion[] regions = Stream.of(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
      .sorted(Comparator.comparingInt(reg -> reg.getStartOffset())).toArray(FoldRegion[]::new);
    assertEquals(6, regions.length);
    
    checkAccessorFolding(regions[0], regions[1], fooClass.getMethods()[0]);
    checkAccessorFolding(regions[2], regions[3], fooClass.getMethods()[1]);

    assertEquals("{...}", regions[4].getPlaceholderText());
    assertEquals("{...}", regions[5].getPlaceholderText());
  }

  public static void checkAccessorFolding(FoldRegion region1, FoldRegion region2, PsiMethod method) {
    assertEquals(region1.getStartOffset(), method.getParameterList().getTextRange().getEndOffset());
    assertEquals(region1.getEndOffset(), method.getBody().getStatements()[0].getTextRange().getStartOffset());
    assertEquals(" { ", region1.getPlaceholderText());

    assertEquals(region2.getStartOffset(), method.getBody().getStatements()[0].getTextRange().getEndOffset());
    assertEquals(region2.getEndOffset(), method.getTextRange().getEndOffset());
    assertEquals(" }", region2.getPlaceholderText());
    assertEquals(region1.getGroup(), region2.getGroup());
  }

  public void test_fold_one_line_methods() {
    @Language("JAVA") String text = """
     class Foo {
      @Override
      int someMethod() {
        return 0;
      }

      int someOtherMethod(
        int param) {
        return 0;
      }

     }""";
    configure(text);
    PsiClass fooClass = JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject()));
    FoldRegion[] regions = Stream.of(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
      .sorted(Comparator.comparingInt(reg -> reg.getStartOffset())).toArray(FoldRegion[]::new);
    assertEquals(3, regions.length);
    checkAccessorFolding(regions[0], regions[1], fooClass.getMethods()[0]);
  }

  public void test_don_t_inline_array_methods() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
     class Foo {
      int arrayMethod(int param)[] {
        return new int[0];
      }

     }""";
    configure(text);
    assertEquals(1, myFixture.getEditor().getFoldingModel().getAllFoldRegions().length);
  }

  public void test_don_t_inline_very_long_one_line_methods() {
    @Language("JAVA") String text = """
      class Foo {
        int someVeryVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVariable;

        // don't create folding that would exceed the right margin
        int getSomeVeryVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVariable() {
          return someVeryVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVariable;
        }
      }
      """;
    configure(text);
    FoldRegion[] regions = Stream.of(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
      .sorted(Comparator.comparingInt(reg -> reg.getStartOffset())).toArray(FoldRegion[]::new);
    assertEquals(1, regions.length);
    assertEquals("{...}", regions[0].getPlaceholderText());
  }

  private void changeFoldRegions(Runnable op) {
    myFixture.getEditor().getFoldingModel().runBatchFoldingOperationDoNotCollapseCaret(op);
  }

  public void test_unselect_word_should_go_inside_folding_group() {
    @Language("JAVA") String text = """
     class Foo {
      int field;

      int getField() {
        return field;
      }

     }""";
    configure(text);
    UsefulTestCase.assertSize(2, myFixture.getEditor().getFoldingModel().getAllFoldRegions());
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("return"));
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    assertEquals("""
     int getField() {
        return field;
      }""", myFixture.getEditor().getSelectionModel().getSelectedText());

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET);
    assertEquals(" {\n   return field;\n }", myFixture.getEditor().getSelectionModel().getSelectedText());
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET);
    assertEquals("return field;", myFixture.getEditor().getSelectionModel().getSelectedText());
  }

  public void test_expand_and_collapse_regions_in_selection() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Foo {
          public static void main() {
              new Runnable(){
                  public void run() {
                  }
              }.run();
          }
      }""";
    configure(text);
    assertEquals(3, getFoldRegionsCount());
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_ALL_REGIONS);
    assertEquals(3, getExpandedFoldRegionsCount());


    myFixture.getEditor().getSelectionModel().setSelection(text.indexOf("new"), text.indexOf("run();"));
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    assertEquals(1, getExpandedFoldRegionsCount());
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_ALL_REGIONS);
    assertEquals(3, getExpandedFoldRegionsCount());
  }

  public void test_expand_and_collapse_recursively() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Foo {
          public static void main() {
              new Runnable(){
                  public void run() {
                  }
              }.run();
          }
      }""";
    configure(text);
    assertEquals(3, getFoldRegionsCount());
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_ALL_REGIONS);
    assertEquals(3, getExpandedFoldRegionsCount());


    myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("new"));
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_REGION_RECURSIVELY);
    assertEquals(1, getExpandedFoldRegionsCount());
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_REGION_RECURSIVELY);
    assertEquals(3, getExpandedFoldRegionsCount());
  }

  public void test_expand_to_level() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Foo {
          public static void main() {
              new Runnable(){
                  public void run() {
                  }
              }.run();
          }
      }""";
    configure(text);
    assertEquals(3, getFoldRegionsCount());

    myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("new"));
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_TO_LEVEL_1);
    assertEquals(2, getExpandedFoldRegionsCount());
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_ALL_TO_LEVEL_1);
    assertEquals(1, getExpandedFoldRegionsCount());
  }

  public void test_expand_recursively_on_expanded_region_containing_collapsed_regions() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Foo {
          public static void main() {
              new Runnable(){
                  public void run() {
                  }
              }.run();
          }
      }""";
    configure(text);
    assertEquals(3, getFoldRegionsCount());
    myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("run"));
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_REGION);
    myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("new"));
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_REGION);
    assertEquals(1, getExpandedFoldRegionsCount());

    myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("main"));
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_REGION_RECURSIVELY);
    assertEquals(3, getExpandedFoldRegionsCount());
  }

  public void test_folding_state_is_preserved_for_unchanged_text_in_bulk_mode() {
    @Language("JAVA") String text = """
      class Foo {
          void m1() {

          }
          void m2() {

          }
      }""";
    configure(text);
    assertEquals(2, getFoldRegionsCount());
    assertEquals(2, getExpandedFoldRegionsCount());
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    assertEquals(0, getExpandedFoldRegionsCount());

    DocumentEx document = (DocumentEx)myFixture.getEditor().getDocument();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () ->
      DocumentUtil.executeInBulk(document, () -> document.insertString(document.getText().indexOf("}") + 1, "\n")));
    assertEquals(2, getFoldRegionsCount());
    assertEquals(0, getExpandedFoldRegionsCount());
  }

  public void test_processing_of_tabs_inside_fold_regions() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      public class Foo {
      \tpublic static void main(String[] args) {
      \t\tjavax.swing.SwingUtilities.invokeLater(new Runnable() {
      \t\t\t@Override
      \t\t\tpublic void run() {
      \t\t\t\tSystem.out.println();
      \t\t\t}
      \t\t});
      \t}
      }""";

    configure(text);
    assertNotNull(myFixture.getEditor().getFoldingModel().getCollapsedRegionAtOffset(text.indexOf("new")));
    myFixture.getEditor().

      getSettings().setUseTabCharacter(true);
    EditorTestUtil.configureSoftWraps(myFixture.getEditor(), 1000);
    myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("System"));
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB);
    myFixture.checkResult("""
        public class Foo {
        \tpublic static void main(String[] args) {
        \t\tjavax.swing.SwingUtilities.invokeLater(new Runnable() {
        \t\t\t@Override
        \t\t\tpublic void run() {
        \t\t\t\t\t<caret>System.out.println();
        \t\t\t}
        \t\t});
        \t}
        }""");
  }

  public void testCollapseExistingButExpandedBlock() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Foo {
        void m() {
        if (true) {
            System.out.println();
        }
        }
      }""";

    configure(text);
    myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("System"));
    myFixture.performEditorAction("CollapseBlock");
    myFixture.performEditorAction("ExpandAllRegions");
    myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("System"));
    myFixture.performEditorAction("CollapseBlock");

    FoldRegion[] topLevelRegions = ((FoldingModelEx)myFixture.getEditor().getFoldingModel()).fetchTopLevel();
    assertEquals(1, topLevelRegions.length);
    assertEquals(topLevelRegions[0].getStartOffset(), text.indexOf("{", text.indexOf("if")));
    assertEquals(topLevelRegions[0].getEndOffset(), text.indexOf("}", text.indexOf("if")) + 1);
  }

  public void test_editing_near_closure_folding() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
        class Foo {
          void m() {
            SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println();
                    }
                });
          }
        }
        """;
    configure(text);
    assertTopLevelFoldRegionsState("[FoldRegion +(56:143), placeholder='(Runnable) () → { ', FoldRegion +(164:188), placeholder=' }']");
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("SwingUtilities"));
    myFixture.type(" ");
    myFixture.doHighlighting();
    assertTopLevelFoldRegionsState("[FoldRegion +(57:144), placeholder='(Runnable) () → { ', FoldRegion +(165:189), placeholder=' }']");
  }

  public void test_folding_update_after_external_change() {
    @Language("JAVA") String text = """
      class Foo {
        void m1() {
          System.out.println(1);
          System.out.println(2);
        }

        void m2() {
          System.out.println(3);
          System.out.println(4);
        }
      }""";
    configure(text);
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    assertTopLevelFoldRegionsState("[FoldRegion +(24:83), placeholder='{...}', FoldRegion +(97:156), placeholder='{...}']");

    VirtualFile virtualFile = myFixture.getEditor().getVirtualFile();
    myFixture.saveText(virtualFile, """
      class Foo {
        void m1() {
          System.out.println(1);
          System.out.println(4);
        }
      }""");
    virtualFile.refresh(false, false);

    myFixture.doHighlighting();
    assertTopLevelFoldRegionsState("[FoldRegion +(24:83), placeholder='{...}']");
  }

  public void test_placeholder_update_on_refactoring() {
    @SuppressWarnings("ALL") @Language("JAVA") String text = """
      class Foo {
        void method() {}
      
        Foo foo = new Foo() {
          void method() {
            System.out.println();
          }
        };
      }""";
    configure(text);
    assertTopLevelFoldRegionsState("[FoldRegion +(44:82), placeholder='method() → { ', FoldRegion +(103:113), placeholder=' }']");

    // emulate rename refactoring ('method' to 'otherMethod')
    Document document = myFixture.getEditor().getDocument();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      int pos;
      while ((pos = document.getText().indexOf("method")) >= 0) {
        document.replaceString(pos, pos + "method".length(), "otherMethod");
      }
    });

    myFixture.doHighlighting();
    assertTopLevelFoldRegionsState("[FoldRegion +(49:92), placeholder='otherMethod() → { ', FoldRegion +(113:123), placeholder=' }']");
  }

  public void test_imports_remain_collapsed_when_new_item_is_added_at_the_end() {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);// tests disable this by default
      ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
      configure("""
        import java.util.ArrayList;
        import java.util.List;
        
        class Foo {
            public static void main(String[] args) {
                Class a = ArrayList.class;
                Class l = List.class;
                <caret>
            }
        }""");
      assertTopLevelFoldRegionsState("[FoldRegion +(7:50), placeholder='...']");

      myFixture.type("Class t = TreeMap.class;");
      myFixture.doHighlighting();// let auto-import complete
      myFixture.checkResult("""
        import java.util.ArrayList;
        import java.util.List;
        import java.util.TreeMap;
        
        class Foo {
            public static void main(String[] args) {
                Class a = ArrayList.class;
                Class l = List.class;
                Class t = TreeMap.class;<caret>
            }
        }""");
      myFixture.doHighlighting();// update folding for the new text
      assertTopLevelFoldRegionsState("[FoldRegion +(7:76), placeholder='...']");
      return null;
    });
  }

  public void testGroupedFoldingsAreNotUpdatedOnUnrelatedDocumentChange() {
    configure("""
        class Foo {
          void m() {
            SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println();
                    }
                });
          }
        }""");
    assertTopLevelFoldRegionsState("[FoldRegion +(56:143), placeholder='(Runnable) () → { ', FoldRegion +(164:188), placeholder=' }']");

    ((FoldingModelEx)myFixture.getEditor().getFoldingModel()).addListener(new FoldingListener() {
      @Override
      public void onFoldRegionStateChange(@NotNull FoldRegion region) {
        fail("Unexpected fold region change");
      }

      @Override
      public void onFoldProcessingEnd() {
        fail("Unexpected fold regions change");
      }
    }, myFixture.getTestRootDisposable());
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("SwingUtilities"));
    myFixture.type(" ");
    myFixture.doHighlighting();
  }

  private void assertTopLevelFoldRegionsState(String expectedState) {
    assertEquals(expectedState, myFixture.getEditor().getFoldingModel().toString());
  }

  private int getFoldRegionsCount() {
    return myFixture.getEditor().getFoldingModel().getAllFoldRegions().length;
  }

  private int getExpandedFoldRegionsCount() {
    return (int)Stream.of(myFixture.getEditor().getFoldingModel().getAllFoldRegions()).filter(FoldRegion::isExpanded).count();
  }

  private boolean assertFolding(final int offset) {
    assertTrue(offset >= 0);
    return ContainerUtil.exists(myFixture.getEditor().getFoldingModel().getAllFoldRegions(), reg -> reg.getStartOffset() == offset);
  }

  private void assertFolding(String marker) {
    assertTrue(marker, assertFolding(myFixture.getFile().getText().indexOf(marker)));
  }

  private static void collapse(FoldRegion propOwner) {
    propOwner.setExpanded(false);
  }
}
