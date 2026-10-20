package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.application.options.CodeStyle;
import com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesTest;
import com.intellij.codeInsight.editorActions.JavaCopyPasteReferenceProcessor;
import com.intellij.ide.DataManager;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.CopiedFromEmptySelectionPasteMode;
import com.intellij.openapi.editor.impl.EditorCopyPasteHelperImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ThrowableRunnable;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.awt.Point;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaCodeInsightFixture;
import static com.intellij.testFramework.JavaCodeInsightFixtureKt.setUpJdk;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class CopyPasteTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<Path> pathFixture = tempPathFixture();
  private final TestFixture<Module> moduleFixture = moduleFixture(projectFixture, pathFixture, true);
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaCodeInsightFixture(projectFixture, pathFixture);

  private static final String BASE_PATH = "/codeInsight/copyPaste";

  @TestDisposable
  Disposable disposable;

  private JavaCodeInsightTestFixture myFixture;
  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
    myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath());
    Module module = moduleFixture.get();
    AcceptedLanguageLevelsSettings.allowLevel(disposable, LanguageLevel.JDK_21_PREVIEW);
    EdtTestUtil.runInEdtAndWait(() -> setUpJdk(LanguageLevel.JDK_21_PREVIEW, projectFixture.get(), module, disposable));
    IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_21_PREVIEW, disposable);
    ModuleRootModificationUtil.updateModel(module, DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  /**
   * Runs the test body on the EDT with {@link CodeInsightSettings#ADD_IMPORTS_ON_PASTE} enabled.
   */
  private static <E extends Throwable> void onEdt(@NotNull ThrowableRunnable<E> body) throws E {
    EdtTestUtil.runInEdtAndWait(() -> CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES;
      body.run();
      return null;
    }));
  }

  @Test
  public void testSimple() { onEdt(this::doTest); }

  @Test
  public void testCollapsedImport() {
    onEdt(() -> {
      doTest();

      doRemoveImportTest(Collections.singleton("java.util.List"));
    });
  }

  private void doRemoveImportTest(Set<String> toRemove) {
    PsiJavaFile javaFile = (PsiJavaFile)myFixture.configureByFile(getAfterPath("java"));
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> JavaCopyPasteReferenceProcessor
      .removeImports(javaFile, toRemove));
    myFixture.checkResultByFile(getTestFilePath(".after1.java"));
  }

  @Test
  public void testCopyWS() { onEdt(this::doTest); }
  @Test
  public void testRefs() { onEdt(this::doTest); }
  @Test
  public void testRefsOnInnerClass() { onEdt(this::doTest); }
  @Test
  public void testClassIntoPackage() { onEdt(this::doTest); }
  @Test
  public void testIndentBlock() { onEdt(this::doTest); }
  @Test
  public void testCarriageReturn() { onEdt(this::doTest); }
  @Test
  public void testCharCarriageReturn() { onEdt(this::doTest); }
  @Test
  public void testPasteLineEndingWithNewlineInString() { onEdt(this::doTest); }
  @Test
  public void testPasteEntireLineOutsideString() { onEdt(this::doTest); }
  @Test
  public void testPasteEntireLineInsideTextBlock() { onEdt(this::doTest); }
  @Test
  public void testCopyFromStringToString() { onEdt(this::doTest); }
  @Test
  public void testCopyFromCharToChar() { onEdt(this::doTest); }
  @Test
  public void testPasteInSelection() { onEdt(this::doTest); }
  @Test
  public void testCopyFromTextBlockToString() { onEdt(this::doTest); }
  @Test
  public void testCopyFromStringTemplateToStringTemplate() { onEdt(this::doTest); }

  @Test
  public void testPasteInCompositeSelectedStringLiteral() {
    onEdt(() -> {
      CommonCodeStyleSettings javaSettings = CodeStyle.getSettings(projectFixture.get()).getCommonSettings(JavaLanguage.INSTANCE);
      boolean align = javaSettings.ALIGN_MULTILINE_BINARY_OPERATION;
      try {
        javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;
        doTest();
      }
      finally {
        javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = align;
      }
    });
  }

  @Test
  public void testOverwriteBlock() { onEdt(this::doTest); }
  @Test
  public void testSimpleAdjustment() { onEdt(this::doTest); }
  @Test
  public void testAdjustLineIndentWhenPaste() { onEdt(this::doTest); }

  @Test
  public void testFieldRefInsideJavadoc() {
    onEdt(() -> {
      myFixture.configureByFile(getTestFilePath(".java"));
      copyReference();
      EditorActionManager actionManager = EditorActionManager.getInstance();
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_PASTE);
      actionHandler.execute(myFixture.getEditor(), null, DataManager.getInstance().getDataContextFromFocus().getResultSync());
      myFixture.checkResultByFile(getTestFilePath(".after.java"));
    });
  }

  @Test
  public void testShortMethodRefInsideJavadoc() {
    onEdt(() -> {
      myFixture.configureByFile(getTestFilePath(".java"));
      final PsiFile file = myFixture.getFile();
      assertInstanceOf(PsiJavaFile.class, file);
      final PsiClass psiClass = ((PsiJavaFile)file).getClasses()[0];
      assertNotNull(psiClass);
      final PsiMethod method = psiClass.getMethods()[0];
      assertNotNull(method);
      CopyReferenceAction.doCopy(method, projectFixture.get());
      EditorActionManager actionManager = EditorActionManager.getInstance();
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_PASTE);
      actionHandler.execute(myFixture.getEditor(), null, DataManager.getInstance().getDataContextFromFocus().getResultSync());
      myFixture.checkResultByFile(getTestFilePath(".after.java"));
    });
  }

  @Test
  public void testOctalEscapesInStringLiteral() { onEdt(this::doTest); }

  private void copyReference() {
    final PsiFile file = myFixture.getFile();
    assertInstanceOf(PsiJavaFile.class, file);
    final PsiClass psiClass = ((PsiJavaFile)file).getClasses()[0];
    assertNotNull(psiClass);
    final PsiField psiField = psiClass.getFields()[0];
    assertNotNull(psiField);
    CopyReferenceAction.doCopy(psiField, projectFixture.get());
  }

  /*
  public void _testXmlPaste() {
    doTest("xml");
  }

  public void _testXmlCdataPaste() {
    doTest("xml");
  }

  public void _testXmlAttributePaste() {
    doTest("xml");
  }
  */

  @Test
  public void testSCR5991() {
    onEdt(() -> {
      final CodeStyleSettings rootSettings = CodeStyle.createTestSettings(CodeStyle.getSettings(projectFixture.get()));
      CommonCodeStyleSettings cstyle = rootSettings.getCommonSettings(JavaLanguage.INSTANCE);
      cstyle.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
      cstyle.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
      cstyle.KEEP_LINE_BREAKS = false;
      cstyle.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
      CodeStyle.doWithTemporarySettings(projectFixture.get(), rootSettings, () -> {
        CodeInsightSettings.runWithTemporarySettings(settings -> {
          settings.REFORMAT_ON_PASTE = CodeInsightSettings.INDENT_EACH_LINE;
          doTest();
          return null;
        });
      });
    });
  }

  @Test
  public void testCopyPasteInsideStringLiteral() {
    onEdt(() -> {
      myFixture.configureByFile(getTestFilePath(".java"));
      performCopy();
      performPaste();
      myFixture.checkResultByFile(getTestFilePath(".after.java"));
    });
  }

  @Test
  public void testPasteStringLiteralThatContainsLineFeed() { onEdt(this::doTest); }
  @Test
  public void testPasteLineThatEndsByLineFeed() { onEdt(() -> doTestWithReformatOnPaste(CodeInsightSettings.INDENT_EACH_LINE)); }
  @Test
  public void testPasteToTheLineStartLineThatEndsByLineFeed() { onEdt(() -> doTestWithReformatOnPaste(CodeInsightSettings.INDENT_EACH_LINE)); }

  @Test
  public void testImportStatements() {
    onEdt(() -> {
      myFixture.addClass("""
      package foo.copy.past;
      
      public final class Something {
        static void valueOf(int i) { }
      
        static class ArrayList<T> { }
      }
      """);
      doTest();
    });
  }

  @Test
  public void testDontImportUnambiguousClasses() {
    onEdt(() -> {
      CodeInsightSettings.runWithTemporarySettings(settings -> {
        settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
        doTest("java");
        checkAutoImport();
        return null;
      });
    });
  }

  private void checkAutoImport() {
    myFixture.configureByFile(getAfterPath("java"));
    myFixture.type("//"); //touch file to allow pass to make changes
    PsiDocumentManager.getInstance(projectFixture.get()).commitAllDocuments();
    DaemonRespondToChangesTest.makeEditorWindowVisible(new Point(), myFixture.getEditor());
    CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), ArrayUtilRt.EMPTY_INT_ARRAY, true);
    myFixture.checkResultByFile(getTestFilePath(".afterHighlighting." + "java"));
  }

  @Test
  public void testDontImportUnambiguousMembers() {
    onEdt(() -> {
      myFixture.addClass("""
                           package foo; public class Foo {
                             public static <T> T max(java.util.Collection<? extends T> coll) {}
                           }""");

      CodeInsightSettings.runWithTemporarySettings(settings -> {
        settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
        settings.ADD_MEMBER_IMPORTS_ON_THE_FLY = true;
        doTest("java");
        return null;
      });
    });
  }

  @Test
  public void testImportUnambiguousConstants() {
    onEdt(() -> {
      myFixture.addClass("package foo; public class Foo {\n" +
                         "  public static final int MAIN_CONSTANT = 42;" +
                         "}");

      CodeInsightSettings.runWithTemporarySettings(settings -> {
        settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
        doTest("java");
        return null;
      });
    });
  }

  @Test
  public void testCopyInColumnSelectionModeWithStringPartsSelected() {
    onEdt(() -> {
      String name = testName;
      myFixture.configureByFile(getTestFilePath(".src.java"));

      ((EditorEx)myFixture.getEditor()).setColumnMode(true);
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);

      performCopy();
      myFixture.configureByText(name + ".dst.txt", "");
      performPaste();
      checkResult("a\" + \na\";  ");
    });
  }

  @Test
  public void testPsiCopyPasteManagerLeak() throws IOException {
    onEdt(() -> {
      File projectDir = FileUtil.createTempDirectory("project", null);
      File srcDir = new File(projectDir, "src");
      FileUtil.createDirectory(srcDir);
      File testFile = new File(srcDir, "A.java");
      FileUtil.writeToFile(testFile, "class A { }");
      VirtualFile vFile = StandardFileSystems.local().refreshAndFindFileByPath(testFile.getAbsolutePath());
      assertNotNull(vFile, testFile.getPath());

      Project project = PlatformTestUtil.loadAndOpenProject(projectDir.toPath().resolve("project.ipr"), disposable);
      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
      try {
        Project finalProject = project;
        Module module = WriteAction.compute(() -> {
          return ModuleManager.getInstance(finalProject).newModule(projectDir + "/module.iml", JavaModuleType.getModuleType().getId());
        });
        PsiTestUtil.addSourceContentToRoots(module, vFile.getParent());
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        assertNotNull(psiFile);

        CopyPasteManager clipboard = CopyPasteManager.getInstance();
        clipboard.setContents(new StringSelection("-"));
        assertThat(clipboard.getContents()).isInstanceOf(StringSelection.class);

        PsiCopyPasteManager.getInstance().setElements(new PsiElement[]{psiFile}, false);
        assertThat(PsiCopyPasteManager.getInstance().getElements(new boolean[1])).isEqualTo(new PsiElement[]{psiFile});

        assertTrue(projectManager.forceCloseProject(project));
        project = null;
        assertThat(clipboard.getContents()).isInstanceOf(StringSelection.class);
      }
      finally {
        if (project != null) {
          projectManager.forceCloseProject(project);
        }
        FileUtil.delete(projectDir);
      }
    });
  }

  @Test
  public void testPasteAfterBlockSelection() {
    onEdt(() -> {
      myFixture.configureByFile(getTestFilePath(".java"));
      performCopy();

      new EditorMouseFixture((EditorImpl)myFixture.getEditor()).clickAt(6, 8);

      performPaste();
      myFixture.checkResultByFile(getAfterPath("java"));
    });
  }

  @Test
  public void testPartialLiteralCopy() {
    onEdt(() -> {
      doTest();
    });
  }

  @Test
  public void testMultilinePasteIntoLineComment() {
    onEdt(() -> {
      configureJava("""
                      class C {
                          //<caret>
                      }""");
      putTextToClipboard("multiline\ntext");
      performPaste();
      checkResult("""
                    class C {
                        //multiline
                        //text<caret>
                    }""");
    });
  }

  @Test
  public void testMultilinePasteEndingWithLineBreakIntoLineComment() {
    onEdt(() -> {
      configureJava("""
                      class C {
                          //<caret>
                      }""");
      putTextToClipboard("multiline\ntext\n");
      performPaste();
      checkResult("""
                    class C {
                        //multiline
                        //text<caret>
                    }""");
    });
  }

  @Test
  public void testMultilinePasteIntoJavadoc() {
    onEdt(() -> {
      configureJava("""
                      /**
                       * <caret>
                       */
                      class C {}""");
      putTextToClipboard("multiline\ntext");
      performPaste();
      checkResult("""
                    /**
                     * multiline
                     * text<caret>
                     */
                    class C {}""");
    });
  }

  @Test
  public void testPasteIntoJavadocAtLineStart() {
    onEdt(() -> {
      configureJava("""
                      /**
                      <caret> *
                       */
                      class C {}""");
      putTextToClipboard(" * foo\n");
      performPaste();
      checkResult("""
                    /**
                     * foo
                     *
                     */
                    class C {}""");
    });
  }

  @Test
  public void testPastingAtLineEndOfLineComment() {
    onEdt(() -> {
      configureJava("""
                      class C {
                        // comment<selection><caret>
                        int a;</selection>
                      }""");
      performCopy();
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE); // remove selection
      performPaste();
      checkResult("""
                    class C {
                      // comment
                      int a;<caret>
                      int a;
                    }""");
    });
  }

  @Test
  public void testPastingAtLineEndOfDocComment() {
    onEdt(() -> {
      configureJava("""
                      /**
                       * foo<selection>
                       * bar<caret></selection>
                       */
                      class C {}""");
      performCopy();
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE); // remove selection
      performPaste();
      checkResult("""
                    /**
                     * foo
                     * bar
                     * bar<caret>
                     */
                    class C {}""");
    });
  }

  @Test
  public void testPasteInMarkdownCodeBlock() {
    onEdt(() -> {
      // paste code taken from IDEA-373755
      configureJava("""
                      ///```java
                      /// <caret>
                      ///```
                      class C {}
                      """);
      putTextToClipboard("""
                           @Override
                           protected Set<GrantedAuthority> getGrantedAuthorities() {
                             return Set.of(new GrantedAuthority("ROLE_NAME"));
                           }""");
      performPaste();
      checkResult("""
                    ///```java
                    /// @Override
                    /// protected Set<GrantedAuthority> getGrantedAuthorities() {
                    ///   return Set.of(new GrantedAuthority("ROLE_NAME"));
                    /// }
                    ///```
                    class C {}
                    """);
    });
  }

  @Test
  public void testCopyPasteEmptyFile() {
    onEdt(() -> {
      configureJava("<caret>");
      performCopy();
      performPaste();
      checkResult("\n" +
                  "<caret>");
    });
  }

  @Test
  public void testCopyPasteEntireLineFromEmptySelection() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.ENTIRE_LINE_ABOVE_CARET);

      configureJava("""
                      class EntireLineFromEmptySelection {
                          int a = 42;
                          char foo = 'A<caret>';
                      }""");
      performCopy();
      configureJava("""
                      class EntireLineFromEmptySelection {
                          int a<caret> = 42;
                          char foo = 'A';
                      }""");
      performPaste();
      checkResult("""
                    class EntireLineFromEmptySelection {
                        char foo = 'A';
                        int a<caret> = 42;
                        char foo = 'A';
                    }""");
    });
  }

  @Test
  public void testCopyPasteEntireLineFromEmptySelectionWithSelectionOnPaste() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.ENTIRE_LINE_ABOVE_CARET);

      configureJava("""
                      class EntireLineFromEmptySelection {
                          int a = 42;
                          char foo = 'A<caret>';
                      }""");
      performCopy();
      configureJava("""
                      class EntireLineFromEmptySelection {
                          int <selection>a<caret> = 42</selection>;
                          char foo = 'A';
                      }""");
      performPaste();
      checkResult("""
                    class EntireLineFromEmptySelection {
                        int     char foo = 'A';
                        ;
                        char foo = 'A';
                    }""");
    });
  }

  @Test
  public void testCopyPasteEntireLineFromEmptySelectionLastLine() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.ENTIRE_LINE_ABOVE_CARET);

      configureJava("class <caret>EntireLineFromEmptySelectionLastLine {}");
      performCopy();
      configureJava("class <caret>EntireLineFromEmptySelectionLastLine {}");
      performPaste();
      checkResult("class EntireLineFromEmptySelectionLastLine {}\n" +
                  "class <caret>EntireLineFromEmptySelectionLastLine {}");
    });
  }

  @Test
  public void testCopyPasteFromEmptySelectionTrimMiddleLine() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.TRIM_IF_MIDDLE_LINE);

      configureJava("""
                      class CopyPasteFromEmptySelection {
                          class Inner {}
                          char foo = 'A<caret>';
                      }""");
      performCopy();
      configureJava("""
                      class CopyPasteFromEmptySelection {
                          class Inner {<caret>}
                          char foo = 'A';
                      }""");
      performPaste();
      checkResult("""
                    class CopyPasteFromEmptySelection {
                        class Inner {char foo = 'A';<caret>}
                        char foo = 'A';
                    }""");
    });
  }

  @Test
  public void testCopyPasteFromEmptySelectionTrimMiddleLinePasteWithSelection() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.TRIM_IF_MIDDLE_LINE);

      configureJava("""
                          private void test() {
                              int i;
                              i = <caret>getOffset();
                              if (i < 0) {
                                error();
                              }
                          }""");
      performCopy();
      configureJava("""
                          private void test() {
                              int i;
                              i = getOffset();
                              if (<selection><caret>i</selection> < 0) {
                                error();
                              }
                          }""");
      performPaste();
      checkResult("""
                          private void test() {
                              int i;
                              i = getOffset();
                              if (i = getOffset();<caret> < 0) {
                                error();
                              }
                          }""");
    });
  }

  @Test
  public void testCopyPasteFromEmptySelectionTrimMiddleLinePasteAtLineStart() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.TRIM_IF_MIDDLE_LINE);

      configureJava("""
                      class CopyPasteFromEmptySelection {
                          class Inner {}
                          char foo = 'A<caret>';
                      }""");
      performCopy();
      configureJava("""
                      <caret>class CopyPasteFromEmptySelection {
                          class Inner {}
                          char foo = 'A';
                      }""");
      performPaste();
      checkResult("""
                    char foo = 'A';
                    <caret>class CopyPasteFromEmptySelection {
                        class Inner {}
                        char foo = 'A';
                    }""");
    });
  }

  @Test
  public void testCopyPasteFromEmptySelectionTrimMiddleLinePasteAtLineStartWithSelection() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.TRIM_IF_MIDDLE_LINE);

      configureJava("""
                      class CopyPasteFromEmptySelection {
                          class Inner {}
                          char foo = 'A<caret>';
                      }""");
      performCopy();
      configureJava("""
                      class CopyPasteFromEmptySelection {
                      <selection>    class Inner {<caret>}
                      </selection>    char foo = 'A';
                      }""");
      performPaste();
      checkResult("""
                    class CopyPasteFromEmptySelection {
                        char foo = 'A';
                    <caret>    char foo = 'A';
                    }""");
    });
  }

  @Test
  public void testCopyPasteFromEmptySelectionTwiceDuplicatesLineModeTrimMiddleLine() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.TRIM_IF_MIDDLE_LINE);
      doTestCopyPasteFromEmptySelectionTwiceDuplicatesLine();
    });
  }

  @Test
  public void testCopyPasteFromEmptySelectionTwiceDuplicatesLineModeLineAbove() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.ENTIRE_LINE_ABOVE_CARET);
      doTestCopyPasteFromEmptySelectionTwiceDuplicatesLine();
    });
  }

  @Test
  public void testCopyPasteFromEmptySelectionTwiceDuplicatesLineModeAtCaret() {
    onEdt(() -> {
      setPasteMode(CopiedFromEmptySelectionPasteMode.AT_CARET);
      doTestCopyPasteFromEmptySelectionTwiceDuplicatesLine();
    });
  }

  private void doTestCopyPasteFromEmptySelectionTwiceDuplicatesLine() {
    configureJava("""
                    class CopyPasteFromEmptySelection {
                        char foo = 'A<caret>';
                    }""");
    performCopy();
    checkResult("""
                  class CopyPasteFromEmptySelection {
                  <selection>    char foo = 'A<caret>';
                  </selection>}""");
    performPaste();
    checkResult("""
                  class CopyPasteFromEmptySelection {
                      char foo = 'A';
                  <caret>}""");
    performPaste();
    checkResult("""
                  class CopyPasteFromEmptySelection {
                      char foo = 'A';
                      char foo = 'A';
                  <caret>}""");
  }

  private void setPasteMode(@NotNull CopiedFromEmptySelectionPasteMode newMode) {
    var savedValue = AdvancedSettings.getEnum(EditorCopyPasteHelperImpl.COPIED_FROM_EMPTY_SELECTION_PASTE_MODE, CopiedFromEmptySelectionPasteMode.class);
    Disposer.register(disposable, () -> {
      AdvancedSettings.setEnum(EditorCopyPasteHelperImpl.COPIED_FROM_EMPTY_SELECTION_PASTE_MODE, savedValue);
    });
    AdvancedSettings.setEnum(EditorCopyPasteHelperImpl.COPIED_FROM_EMPTY_SELECTION_PASTE_MODE, newMode);
  }

  private void doTest() {
    doTest("java");
  }

  private void doTestWithReformatOnPaste(@MagicConstant(intValues = {CodeInsightSettings.NO_REFORMAT, CodeInsightSettings.INDENT_BLOCK, CodeInsightSettings.INDENT_EACH_LINE, CodeInsightSettings.REFORMAT_BLOCK}) int actionOnPaste) {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.REFORMAT_ON_PASTE = actionOnPaste;
      doTest("java");
      return null;
    });
  }

  private void doTest(String extension) {
    myFixture.configureByFile(getTestFilePath(".src." + extension));
    performCopy();
    myFixture.configureByFile(getTestFilePath(".dst." + extension));
    performPaste();
    myFixture.checkResultByFile(getAfterPath(extension));
  }

  private String getTestFilePath(String suffix) {
    return BASE_PATH + File.separatorChar + testName + suffix;
  }

  private String getAfterPath(String extension) {
    return getTestFilePath(".after." + extension);
  }

  private void performCopy() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
  }

  private void performPaste() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
  }

  private void configureJava(String fileText) {
    myFixture.configureByText(JavaFileType.INSTANCE, fileText);
  }

  private void checkResult(String expectedFileText) {
    myFixture.checkResult(expectedFileText);
  }

  private static void putTextToClipboard(String text) {
    CopyPasteManager.getInstance().setContents(new StringSelection(text));
  }
}
