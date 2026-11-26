// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.java.codeInsight.daemon.LightAdvHighlightingTest;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.awt.Dimension;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class LightAnnotatorHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, () -> List.of(), LightDaemonAnalyzerOptions.DEFAULT.withProductionDaemon());

  @Test
  public void testInjectedAnnotator() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() ->
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(XmlFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyAnnotator()}, () ->
      daemon.doTest(LightAdvHighlightingTest.BASE_PATH + "/" + daemon.getFixture().getTestName(false) + ".xml", true, false)
    ));
  }

  @Test
  public void testAnnotatorWorksWithFileLevel() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() ->
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyFileLevelAnnotator()}, () -> {
      daemon.configureFromFileText("x.java", """
        class X {
          <caret>
        }
        """);
      setViewportSize(daemon); // whole file fit onscreen
      assertEmpty(daemon.highlightErrors());
      List<HighlightInfo> fileLevel = getFileLevelHighlights(daemon);
      HighlightInfo info = assertOneElement(fileLevel);
      assertTrue(MyFileLevelAnnotator.isMy(info));

      daemon.type("\n\n");
      assertEmpty(daemon.highlightErrors());
      fileLevel = getFileLevelHighlights(daemon);
      info = assertOneElement(fileLevel);
      assertTrue(MyFileLevelAnnotator.isMy(info));
      daemon.type("//xxx"); //disable top level annotation
      List<HighlightInfo> warnings = daemon.doHighlighting(HighlightSeverity.WARNING);
      assertEmpty(warnings);
      fileLevel = getFileLevelHighlights(daemon);
      assertEmpty(fileLevel);
    }));
  }

  private static void setViewportSize(@NotNull LightDaemonAnalyzerFixture daemon) {
    EditorEx editor = (EditorEx)daemon.getFixture().getEditor();
    EdtTestUtil.runInEdtAndWait(() -> editor.getScrollPane().getViewport().setSize(new Dimension(1000, 1000)));
  }

  private static @NotNull List<HighlightInfo> getFileLevelHighlights(@NotNull LightDaemonAnalyzerFixture daemon) {
    PsiFile psiFile = daemon.getFixture().getFile();
    return EdtTestUtil.runInEdtAndGet(() -> daemon.getDaemonCodeAnalyzer().getFileLevelHighlights(daemon.getFixture().getProject(), psiFile));
  }

  private static <T> T assertOneElement(@NotNull List<T> list) {
    assertEquals(1, list.size(), list.toString());
    return list.getFirst();
  }

  private static void assertEmpty(@NotNull List<?> list) {
    assertTrue(list.isEmpty(), list.toString());
  }

  // must stay public for PicoContainer to work
  public static final class MyAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      psiElement.accept(new XmlElementVisitor() {
        @Override
        public void visitXmlTag(@NotNull XmlTag tag) {
          XmlAttribute attribute = tag.getAttribute("aaa", "");
          if (attribute != null) {
            holder.newAnnotation(HighlightSeverity.WARNING, "MyAnnotator").range(attribute).create();
            iDidIt();
          }
        }

        @Override
        public void visitXmlToken(@NotNull XmlToken token) {
          if (token.getTokenType() == XmlTokenType.XML_CHAR_ENTITY_REF) {
            holder.newAnnotation(HighlightSeverity.WARNING, "ENTITY").range(token).create();
            iDidIt();
          }
        }
      });
    }
  }

  // must stay public for PicoContainer to work
  public static class MyFileLevelAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      if (psiElement instanceof PsiFile && !psiElement.getText().contains("xxx")) {
        holder.newAnnotation(HighlightSeverity.WARNING, "top level").fileLevel().create();
        iDidIt();
      }
    }
    static boolean isMy(HighlightInfo info) {
      return HighlightSeverity.WARNING.equals(info.getSeverity()) && "top level".equals(info.getDescription());
    }
  }

  @Test
  public void testAnnotatorMustNotSpecifyCrazyRangeForCreatedAnnotation() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() ->
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyCrazyAnnotator()}, () -> runMyAnnotators(daemon)));
  }
  private static void runMyAnnotators(@NotNull LightDaemonAnalyzerFixture daemon) {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = """
      class X {
        //XXX
      }
      """;
    daemon.configureFromFileText("x.java", text);
    EditorImpl editor = (EditorImpl)daemon.getFixture().getEditor();
    PsiFile psiFile = daemon.getFixture().getFile();
    Project project = daemon.getFixture().getProject();
    EdtTestUtil.runInEdtAndWait(() -> {
      editor.getScrollPane().getViewport().setSize(1000, 1000);
      assertEquals(psiFile.getTextRange(), editor.calculateVisibleRange());
    });

    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      daemon.getTestDaemonCodeAnalyzer().waitForDaemonToFinish(psiFile);
    });
  }

  public static class MyCrazyAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        try {
          iDidIt();
          holder.newAnnotation(HighlightSeverity.ERROR, "xxx"+uniqCount++)
            .range(new TextRange(0,1))
            .create();
          fail("Must have rejected crazy annotation range");
        }
        catch (PluginException ignored) {
        }
      }
    }
  }
  private static int uniqCount;
  private static void checkThrowsWhenCalledTwice(AnnotationHolder holder, Function<? super AnnotationBuilder, ? extends AnnotationBuilder> method) {
    try {
      AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx"+uniqCount++);
      method.apply(builder);
      method.apply(builder);
      builder.create();
      fail("Must have failed");
    }
    catch (IllegalStateException ignored) {
    }
    // once is OK
    AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx"+uniqCount++);
    AnnotationBuilder newBuilder = method.apply(builder);
    assertSame(newBuilder, builder);
    builder.create();
  }
  private static void checkThrowsWhenCalledTwiceOnFixBuilder(AnnotationHolder holder,
                                                             IntentionAction fix,
                                                             Function<? super AnnotationBuilder.FixBuilder, ? extends AnnotationBuilder.FixBuilder> method) {
    checkThrowsWhenCalledOnFixBuilder(holder, fix, method, builder1 -> {
                                        method.apply(builder1);
                                        method.apply(builder1);
                                        return builder1;
                                      }
    );
  }
  private static void checkThrowsWhenCalledTwiceOnFixBuilder(AnnotationHolder holder,
                                                             LocalQuickFix fix,
                                                             Function<? super AnnotationBuilder.FixBuilder, ? extends AnnotationBuilder.FixBuilder> method) {
    checkThrowsWhenCalledOnFixBuilder(holder, fix, method, builder1 -> {
                                        method.apply(builder1);
                                        method.apply(builder1);
                                        return builder1;
                                      }
    );
  }

  private static void checkThrowsWhenCalledOnFixBuilder(@NotNull AnnotationHolder holder,
                                                        @NotNull IntentionAction fix,
                                                        @NotNull Function<? super AnnotationBuilder.FixBuilder, ? extends AnnotationBuilder.FixBuilder> good,
                                                        @NotNull Function<? super AnnotationBuilder.FixBuilder, ? extends AnnotationBuilder.FixBuilder> bad) {
    try {
      AnnotationBuilder.FixBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx"+uniqCount++).newFix(fix);
      bad.apply(builder);
      builder.registerFix().create();
      fail("Must have failed");
    }
    catch (IllegalStateException|IllegalArgumentException ignored) {
    }
    AnnotationBuilder.FixBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx"+uniqCount++).newFix(fix);
    AnnotationBuilder.FixBuilder newBuilder = good.apply(builder);
    assertSame(newBuilder, builder);
    builder.registerFix().create();
  }
  private static void checkThrowsWhenCalledOnFixBuilder(@NotNull AnnotationHolder holder,
                                                        @NotNull LocalQuickFix fix,
                                                        @NotNull Function<? super AnnotationBuilder.FixBuilder, ? extends AnnotationBuilder.FixBuilder> good,
                                                        @NotNull Function<? super AnnotationBuilder.FixBuilder, ? extends AnnotationBuilder.FixBuilder> bad) {
    ProblemDescriptor descriptor = new ProblemDescriptor() {
      @Override
      public PsiElement getPsiElement() {
        return null;
      }

      @Override
      public PsiElement getStartElement() {
        return null;
      }

      @Override
      public PsiElement getEndElement() {
        return null;
      }

      @Override
      public TextRange getTextRangeInElement() {
        return null;
      }

      @Override
      public int getLineNumber() {
        return 0;
      }

      @NotNull
      @Override
      public ProblemHighlightType getHighlightType() {
        return null;
      }

      @Override
      public boolean isAfterEndOfLine() {
        return false;
      }

      @Override
      public void setTextAttributes(TextAttributesKey key) {

      }

      @Nullable
      @Override
      public ProblemGroup getProblemGroup() {
        return null;
      }

      @Override
      public void setProblemGroup(@Nullable ProblemGroup problemGroup) {

      }

      @Override
      public boolean showTooltip() {
        return false;
      }

      @NotNull
      @Override
      public String getDescriptionTemplate() {
        return null;
      }

      @Override
      public @NotNull QuickFix<?> @Nullable [] getFixes() {
        return QuickFix.EMPTY_ARRAY;
      }
    };
    try {
      AnnotationBuilder.FixBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx"+uniqCount++).newLocalQuickFix(fix, descriptor);
      bad.apply(builder);
      builder.registerFix().create();
      fail("Must have failed");
    }
    catch (IllegalStateException|IllegalArgumentException ignored) {
    }
    AnnotationBuilder.FixBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx"+uniqCount++).newLocalQuickFix(fix, descriptor);
    AnnotationBuilder.FixBuilder newBuilder = good.apply(builder);
    assertSame(newBuilder, builder);
    builder.registerFix().create();
  }
  public static class MyIncorrectRepetitiveAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        checkThrowsWhenCalledTwice(holder, builder -> builder.range(element.getTextRange()));
        checkThrowsWhenCalledTwice(holder, builder -> builder.range(element));
        checkThrowsWhenCalledTwice(holder, builder -> builder.range(element.getNode()));
        checkThrowsWhenCalledTwice(holder, builder -> builder.fileLevel());
        checkThrowsWhenCalledTwice(holder, builder -> builder.afterEndOfLine());
        checkThrowsWhenCalledTwice(holder, builder -> builder.enforcedTextAttributes(TextAttributes.ERASE_MARKER));
        checkThrowsWhenCalledTwice(holder, builder -> builder.highlightType(ProblemHighlightType.ERROR));
        checkThrowsWhenCalledTwice(holder, builder -> builder.gutterIconRenderer(new GutterIconRenderer() {
              @Override
              public boolean equals(Object obj) {
                return false;
              }

              @Override
              public int hashCode() {
                return 0;
              }

              @NotNull
              @Override
              public Icon getIcon() {
                return Messages.getQuestionIcon();
              }
            }));
        checkThrowsWhenCalledTwice(holder, builder -> builder.problemGroup(() -> ""));
        checkThrowsWhenCalledTwice(holder, builder -> builder.tooltip(""));
        checkThrowsWhenCalledTwice(holder, builder -> builder.textAttributes(CodeInsightColors.DEPRECATED_ATTRIBUTES));
        IntentionAction stubIntention = new IntentionAction() {
          @NotNull
          @Override
          public String getText() {
            return "?";
          }

          @NotNull
          @Override
          public String getFamilyName() {
            return "";
          }

          @Override
          public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
            return false;
          }

          @Override
          public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {

          }

          @Override
          public boolean startInWriteAction() {
            return false;
          }
        };
        checkThrowsWhenCalledOnFixBuilder(holder, stubIntention, Function.identity(), AnnotationBuilder.FixBuilder::batch); //must not allow for IntentionAction
        checkThrowsWhenCalledOnFixBuilder(holder, stubIntention, Function.identity(), AnnotationBuilder.FixBuilder::universal); //must not allow for IntentionAction

        LocalQuickFix stubFix = new LocalQuickFix(){
          @Nls(capitalization = Nls.Capitalization.Sentence)
          @NotNull
          @Override
          public String getFamilyName() {
            return "stub";
          }

          @Override
          public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {

          }
        };
        checkThrowsWhenCalledTwiceOnFixBuilder(holder, stubFix, AnnotationBuilder.FixBuilder::batch);
        checkThrowsWhenCalledTwiceOnFixBuilder(holder, stubFix, AnnotationBuilder.FixBuilder::universal);
        checkThrowsWhenCalledOnFixBuilder(holder, stubFix, AnnotationBuilder.FixBuilder::universal, builder1 -> {
          builder1 = builder1.batch();
          builder1 = builder1.universal();
          return builder1;
        });
        checkThrowsWhenCalledOnFixBuilder(holder, stubFix, AnnotationBuilder.FixBuilder::universal, builder1 -> {
          builder1 = builder1.universal();
          builder1 = builder1.batch();
          return builder1;
        });

        class MyUniversal implements IntentionAction, LocalQuickFix {
          @Nls(capitalization = Nls.Capitalization.Sentence)
          @NotNull
          @Override
          public String getText() {
            return "?";
          }

          @Nls(capitalization = Nls.Capitalization.Sentence)
          @NotNull
          @Override
          public String getFamilyName() {
            return getText();
          }

          @Override
          public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
            return false;
          }

          @Override
          public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {

          }

          @Override
          public boolean startInWriteAction() {
            return false;
          }

          @Override
          public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {

          }
        }
        MyUniversal stubUniversal = new MyUniversal();
        checkThrowsWhenCalledTwiceOnFixBuilder(holder, (IntentionAction)stubUniversal, AnnotationBuilder.FixBuilder::batch);
        checkThrowsWhenCalledTwiceOnFixBuilder(holder, (IntentionAction)stubUniversal, AnnotationBuilder.FixBuilder::universal);
        checkThrowsWhenCalledOnFixBuilder(holder, (IntentionAction)stubUniversal, AnnotationBuilder.FixBuilder::universal, builder1 -> {
          builder1 = builder1.batch();
          builder1 = builder1.universal();
          return builder1;
        });
        checkThrowsWhenCalledOnFixBuilder(holder, (IntentionAction)stubUniversal, AnnotationBuilder.FixBuilder::universal, builder1 -> {
          builder1 = builder1.universal();
          builder1 = builder1.batch();
          return builder1;
        });


        HighlightDisplayKey myDeadCodeKey = HighlightDisplayKey.findOrRegister(UnusedDeclarationInspectionBase.SHORT_NAME,
                                                                               UnusedDeclarationInspectionBase.getDisplayNameText(), UnusedDeclarationInspectionBase.SHORT_NAME);
        checkThrowsWhenCalledTwiceOnFixBuilder(holder, stubIntention, fixBuilder -> fixBuilder.key(myDeadCodeKey));
        checkThrowsWhenCalledTwiceOnFixBuilder(holder, stubIntention, fixBuilder -> fixBuilder.range(new TextRange(0, 0)));

        try {
          AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx"+uniqCount++);
          builder.create();
          builder.create();
          fail("must not be able to call create() twice");
        }
        catch (IllegalStateException ignored) {
        }
        iDidIt();
      }
    }
  }

  @Test
  public void testAnnotationBuilderMethodsAllowedToBeCalledOnlyOnce() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() ->
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyIncorrectRepetitiveAnnotator()}, () -> runMyAnnotators(daemon)));
  }

  @Test
  public void testDifferentAnnotatorsTryingToHighlightWarningAndErrorToTheSameElementMustNotInterfere() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() ->
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyErrorAnnotator(), new MyWarningAnnotator()}, () -> {
      daemon.configureFromFileText("My.java", "class My {}");
      setViewportSize(daemon); // whole file fit onscreen
      List<HighlightInfo> infos = ContainerUtil.sorted(daemon.doHighlighting(HighlightSeverity.WARNING), Comparator.comparing(HighlightInfo::getSeverity));
      assertEquals(2, infos.size());
      assertTrue(MyWarningAnnotator.isMy(infos.get(0)));
      assertTrue(MyErrorAnnotator.isMy(infos.get(1)));
    }));
  }
  
  @Test
  public void testDifferentAnnotatorsTryingToHighlightInformationAndErrorToTheSameElementMustNotInterfere() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() ->
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyErrorAnnotator(), new MyInfoAnnotator()}, () -> {
      daemon.configureFromFileText("My.java", "class My {}");
      setViewportSize(daemon); // whole file fit onscreen
      List<HighlightInfo> infos = ContainerUtil.sorted(daemon.doHighlighting(HighlightSeverity.INFORMATION), Comparator.comparing(HighlightInfo::getSeverity));
      assertEquals(2, infos.size());
      assertTrue(MyInfoAnnotator.isMy(infos.get(0)));
      assertTrue(MyErrorAnnotator.isMy(infos.get(1)));
    }));
  }

  @Test
  public void testAnnotatorTryingToHighlightErrorAndSymbolAttributesToTheSameElementMustSucceed() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() ->
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyErrorAnnotator(), new MySymbolAnnotator()}, () -> {
      daemon.configureFromFileText("My.java", "class My {}");
      setViewportSize(daemon); // whole file fit onscreen
      List<HighlightInfo> infos = daemon.doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
      assertTrue(ContainerUtil.exists(infos, info -> info.getSeverity().equals(HighlightSeverity.ERROR) && MyErrorAnnotator.isMy(info)), infos.toString());
      assertTrue(ContainerUtil.exists(infos, info -> info.getSeverity().equals(HighlightInfoType.SYMBOL_TYPE_SEVERITY) && MySymbolAnnotator.isMy(info)), infos.toString());
    }));
  }

  public static class MyErrorAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      if (psiElement instanceof PsiClass) {
        holder.newAnnotation(HighlightSeverity.ERROR, "error2").range(((PsiClass)psiElement).getNameIdentifier()).create();
        iDidIt();
      }
    }
    static boolean isMy(HighlightInfo info) {
      return HighlightSeverity.ERROR.equals(info.getSeverity()) && "error2".equals(info.getDescription());
    }
  }
  public static class MyWarningAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      if (psiElement instanceof PsiClass) {
        holder.newAnnotation(HighlightSeverity.WARNING, "warn2").range(((PsiClass)psiElement).getNameIdentifier()).create();
        iDidIt();
      }
    }
    static boolean isMy(HighlightInfo info) {
      return HighlightSeverity.WARNING.equals(info.getSeverity()) && "warn2".equals(info.getDescription());
    }
  }
  public static class MyInfoAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      if (psiElement instanceof PsiClass) {
        holder.newAnnotation(HighlightSeverity.INFORMATION, "info2").range(((PsiClass)psiElement).getNameIdentifier()).create();
        iDidIt();
      }
    }
    static boolean isMy(HighlightInfo info) {
      return HighlightSeverity.INFORMATION.equals(info.getSeverity()) && "info2".equals(info.getDescription());
    }
  }
  public static class MySymbolAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      if (psiElement instanceof PsiClass) {
        holder.newAnnotation(HighlightInfoType.SYMBOL_TYPE_SEVERITY, "symbol2").range(((PsiClass)psiElement).getNameIdentifier()).create();
        iDidIt();
      }
    }
    static boolean isMy(HighlightInfo info) {
      return HighlightInfoType.SYMBOL_TYPE_SEVERITY.equals(info.getSeverity()) && "symbol2".equals(info.getDescription());
    }
  }

  /**
   * Checks that the Platform doesn't add useless "Inspection 'Annotator' options" quick fix. see <a href="https://youtrack.jetbrains.com/issue/WEB-55217"/>
   */
  @Test
  public void testNoFixesOrOptionsMustBeShownWhenAnnotatorProvidedQuickFixWhichIsDisabled() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    daemon.runTest(() -> {
    GlobalInspectionTool tool = new HighlightVisitorBasedInspection().setRunAnnotators(true);
    myFixture.enableInspections(tool);
    assertNotNull(HighlightDisplayKey.find(tool.getShortName()));
    daemon.configureFromFileText("foo.txt", "hello<caret>");
    DisabledQuickFixAnnotator.FIX_ENABLED = true;
    assertEmpty(daemon.doHighlighting());
    daemon.getDaemonCodeAnalyzer().restart(myFixture.getTestName(false));
    DisabledQuickFixAnnotator.FIX_ENABLED = false;
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(PlainTextLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new DisabledQuickFixAnnotator()}, ()-> {
      assertOneElement(daemon.highlightErrors());
      assertEmpty(CodeInsightTestFixtureImpl.getAvailableIntentions(myFixture.getEditor(), myFixture.getFile())
                    .stream()
                    .filter(i -> IntentionActionDelegate.unwrap(i) instanceof EmptyIntentionAction)
                    .toList()); // nothing, not even EmptyIntentionAction
      daemon.getDaemonCodeAnalyzer().restart(myFixture.getTestName(false));
      DisabledQuickFixAnnotator.FIX_ENABLED = true;
      assertFalse(CodeInsightTestFixtureImpl.getAvailableIntentions(myFixture.getEditor(), myFixture.getFile()).isEmpty()); // maybe a lot of CleanupIntentionAction, FixAllIntention etc
    });
    });
  }

  private static class DisabledQuickFixAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    private static volatile boolean FIX_ENABLED;
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element.getText().equals("hello") && !(element instanceof PsiFile)) {
        holder.newAnnotation(HighlightSeverity.ERROR, "i hate it")
          .newFix(new DeleteElementFix(element) {
            @Override
            protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
              return FIX_ENABLED ? super.getPresentation(context, element) : null;
            }
          }.asIntention()).registerFix().create();
        iDidIt();
      }
    }
  }
}