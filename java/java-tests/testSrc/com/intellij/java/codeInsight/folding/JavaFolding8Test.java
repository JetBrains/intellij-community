package com.intellij.java.codeInsight.folding;

import com.intellij.codeInsight.folding.impl.JavaFoldingBuilder;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.impl.FoldingModelImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;

import java.util.Collections;
import java.util.List;

public class JavaFolding8Test extends JavaFoldingTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void test_no_plain_lambda_folding_where_anonymous_class_can_be_real_lambda_but_fold_otherwise() {
    myFixture.addClass("interface Runnable2 { void run(); }");
    myFixture.addClass("abstract class MyAction { public void run(); public void update() {} }");
    String text = """
      class Test {
        void test() {
          Runnable r = new Runnable2() {
            public void run() {
              System.out.println();
            }
          };
          MyAction action = new MyAction() {
            public void run() {
              System.out.println();
            }
          }
        }
      }
      """;
    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();

    assertEquals("run() " + JavaFoldingBuilder.getRightArrow() + " { ",
                 foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction(")).getPlaceholderText());
    assertNull(foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable2(")));
  }

  public void test_closure_folding_when_implementing_a_single_abstract_method_in_a_class() {
    myFixture.addClass("abstract class MyAction { public abstract void run(); }");
    String text = """
      class Test {
        void test() {
          MyAction action = new MyAction() {
            public void run() {
              System.out.println();
            }
          }
        }
      }
      """;
    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();

    final FoldRegion offset = foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction("));
    assertEquals("() " + JavaFoldingBuilder.getRightArrow() + " { ", offset.getPlaceholderText());
  }

  public void test_folding_lambda_with_block_body() {
    myFoldingSettings.setCollapseAnonymousClasses(true);
    String text = """
      class Test {
        void test() {
          Runnable action = () -> {
              System.out.println();
              };
          }
      }
      """;
    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();

    final FoldRegion offset = foldingModel.getCollapsedRegionAtOffset(text.indexOf("System.out.println"));
    assertEquals("{...}", offset.getPlaceholderText());
  }

  public void test_folding_code_blocks() {
    Assume.assumeTrue(Registry.is("java.folding.icons.for.control.flow"));
    String text = """
      class Test {
        void test(boolean b) {
           if (b) {
              System.out.println();
           }
        }
      }
      """;
    configure(text);
    FoldingModelImpl foldingModel = (FoldingModelImpl)myFixture.getEditor().getFoldingModel();

    final int indexOf = text.indexOf("System.out.println");
    assertEquals(List.of("FoldRegion -(36:92), placeholder='{...}'", "FoldRegion -(50:88), placeholder='{...}'"),
                 StreamEx.of(foldingModel.getAllFoldRegions())
                   .filter(region -> region.getStartOffset() < indexOf && indexOf < region.getEndOffset())
                   .map(FoldRegion::toString)
                   .toList());
  }

  public void testParameterAnnotations() {
    myFixture.addClass("""
                @interface Anno {
                    String value();
                }
    """);
    configure("""
                class Some {
                    void m(@Anno("hello " +
                                 "world") int a,
                           @Anno("goodbye " +
                                 "world") int b) {}
                }
                """);
    assertEquals(List.of("FoldRegion -(29:66), placeholder='(...)'",
                         "FoldRegion -(90:129), placeholder='(...)'"),
                 ContainerUtil.map(myFixture.getEditor().getFoldingModel().getAllFoldRegions(), FoldRegion::toString));
  }

  public void testSingleLineAnnotationIsNotCollapsed() {
    myFixture.addClass("""
                @interface Anno {
                    String value();
                }
    """);
    configure("""
                class Some {
                    @Anno("hello world")
                    void m() {}
                }
                """);
    assertEquals(Collections.emptyList(),
                 ContainerUtil.map(myFixture.getEditor().getFoldingModel().getAllFoldRegions(), FoldRegion::toString));

  }

  public void testAnnotationsFoldedIndependently() {
    myFixture.addClass("""
                @interface Anno {
                    String value();
                }
    """);
    myFixture.addClass("""
                @interface Anno1 {
                    String value();
                }
    """);
    configure("""
                class Some {
                    @Anno(
                    "hello world"
                    )
                    @Anno1(
                    "Another hello world!"
                    )
                    void m() {}
                }
                """);
    assertEquals(List.of("FoldRegion -(22:47), placeholder='(...)'", "FoldRegion -(58:92), placeholder='(...)'"),
                 ContainerUtil.map(myFixture.getEditor().getFoldingModel().getAllFoldRegions(), FoldRegion::toString));

  }

  public void testAnnotationsFoldingInDifferentPlaces(){
    myFixture.addClass("""
                @interface Anno {
                    String value();
                }
    """);

    myFixture.addClass("""
                @interface Anno1 {
                    String value();
                }
    """);
    configure("""
                public @Anno(
                        "1"
                )
                class X {
                    @Anno(
                            "2"
                    )
                    private final @Anno1(
                            "3"
                    ) String x = "";
                
                    @Anno(
                            "4"
                    ) public @Anno1(
                            "5"
                    )
                    String m() { return null; }
                }
                """);
    assertEquals(List.of("FoldRegion -(12:27), placeholder='(...)'", "FoldRegion -(47:70), placeholder='(...)'", "FoldRegion -(95:118), placeholder='(...)'",
                         "FoldRegion -(144:167), placeholder='(...)'", "FoldRegion -(181:204), placeholder='(...)'", "FoldRegion -(220:236), placeholder='{...}'"),
                 ContainerUtil.map(myFixture.getEditor().getFoldingModel().getAllFoldRegions(), FoldRegion::toString));
  }

  public void testCommentBeforePackageInfo() {
    configure("package-info.java", """
                // Copyright
                // Shmopyright
                /**
                 * A cool package
                 */
                package com.example;
                <caret>
                """);
    assertEquals(List.of("FoldRegion +(0:27), placeholder='/.../'",
                         "FoldRegion -(28:53), placeholder='/** A cool package */'"),
                 ContainerUtil.map(myFixture.getEditor().getFoldingModel().getAllFoldRegions(), FoldRegion::toString));
  }

  public void testNestedAnnotationWithMultiline() {
    myFixture.addClass("""
                @interface Anno {
                    NestedAnno[] value();
                }
    """);
    myFixture.addClass("""
                @interface NestedAnno {
                    String[] value();
                }
    """);
    configure("""
                class A {
                  @Anno({
                  @NestedAnno({"a",
                               "b"}),
                  @NestedAnno({"c",
                               "d",
                               "e"}),
                  @NestedAnno({})
                  })
                  void f() {}
                }
                """);
    assertEquals(List.of("FoldRegion -(17:146), placeholder='(...)'", "FoldRegion -(33:60), placeholder='(...)'",
                         "FoldRegion -(75:122), placeholder='(...)'"),
                 ContainerUtil.map(myFixture.getEditor().getFoldingModel().getAllFoldRegions(), FoldRegion::toString));
  }
}
