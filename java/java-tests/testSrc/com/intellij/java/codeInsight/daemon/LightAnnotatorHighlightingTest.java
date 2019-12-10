// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonRespondToChangesTest;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.VisibleHighlightingPassFactory;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class LightAnnotatorHighlightingTest extends LightDaemonAnalyzerTestCase {
  public void testInjectedAnnotator() {
    DaemonRespondToChangesTest.useAnnotatorsIn(StdFileTypes.XML.getLanguage(), new DaemonRespondToChangesTest.MyRecordingAnnotator[]{new MyAnnotator()}, () -> {
      doTest(LightAdvHighlightingTest.BASE_PATH + "/" + getTestName(false) + ".xml",true,false);
    });
  }

  public void testAnnotatorWorksWithFileLevel() {
    DaemonRespondToChangesTest.useAnnotatorsIn(StdFileTypes.JAVA.getLanguage(), new DaemonRespondToChangesTest.MyRecordingAnnotator[]{new MyTopFileAnnotator()}, () -> {
      configureByFile(LightAdvHighlightingTest.BASE_PATH + "/" + getTestName(false) + ".java");
      ((EditorEx)getEditor()).getScrollPane().getViewport().setSize(new Dimension(1000,1000)); // whole file fit onscreen
      doHighlighting();
      List<HighlightInfo> fileLevel =
        ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).getFileLevelHighlights(getProject(), getFile());
      HighlightInfo info = assertOneElement(fileLevel);
      assertEquals("top level", info.getDescription());

      type("\n\n");
      doHighlighting();
      fileLevel =
        ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).getFileLevelHighlights(getProject(), getFile());
      info = assertOneElement(fileLevel);
      assertEquals("top level", info.getDescription());

      type("//xxx"); //disable top level annotation
      List<HighlightInfo> warnings = doHighlighting(HighlightSeverity.WARNING);
      assertEmpty(warnings);
      fileLevel = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).getFileLevelHighlights(getProject(), getFile());
      assertEmpty(fileLevel);
    });
  }

  // must stay public for PicoContainer to work
  public static final class MyAnnotator extends DaemonRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      psiElement.accept(new XmlElementVisitor() {
        @Override
        public void visitXmlTag(XmlTag tag) {
          XmlAttribute attribute = tag.getAttribute("aaa", "");
          if (attribute != null) {
            holder.createWarningAnnotation(attribute, "MyAnnotator");
            iDidIt();
          }
        }

        @Override
        public void visitXmlToken(XmlToken token) {
          if (token.getTokenType() == XmlTokenType.XML_CHAR_ENTITY_REF) {
            holder.createWarningAnnotation(token, "ENTITY");
            iDidIt();
          }
        }
      });
    }
  }

  // must stay public for PicoContainer to work
  public static class MyTopFileAnnotator extends DaemonRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      if (psiElement instanceof PsiFile && !psiElement.getText().contains("xxx")) {
        Annotation annotation = holder.createWarningAnnotation(psiElement, "top level");
        annotation.setFileLevelAnnotation(true);
        iDidIt();
      }
    }
  }

  public void testAnnotatorMustNotSpecifyCrazyRangeForCreatedAnnotation() {
    DaemonRespondToChangesTest.useAnnotatorsIn(StdFileTypes.JAVA.getLanguage(), new DaemonRespondToChangesTest.MyRecordingAnnotator[]{new MyCrazyAnnotator()}, () -> runMyAnnotators()
    );
  }
  private void runMyAnnotators() {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = "class X {\n" +
                  "  //XXX\n" +
                  "}\n";
    configureFromFileText("x.java", text);
    ((EditorImpl)getEditor()).getScrollPane().getViewport().setSize(1000, 1000);
    assertEquals(getFile().getTextRange(), VisibleHighlightingPassFactory.calculateVisibleRange(getEditor()));

    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject()))
      .runPasses(getFile(), getEditor().getDocument(), Collections.singletonList(textEditor), ArrayUtilRt.EMPTY_INT_ARRAY, false, null);
  }

  public static class MyCrazyAnnotator extends DaemonRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        try {
          iDidIt();
          holder.newAnnotation(HighlightSeverity.ERROR, "xxx")
            .range(new TextRange(0,1))
            .create();
          fail("Must have rejected crazy annotation range");
        }
        catch (IllegalArgumentException ignored) {
        }
      }
    }
  }


  private static void checkThrowsWhenCalledTwice(AnnotationHolder holder, Function<? super AnnotationBuilder, ? extends AnnotationBuilder> method) {
    try {
      AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx");
      method.apply(builder);
      method.apply(builder);
      builder.create();
      fail("Must have failed");
    }
    catch (IllegalStateException ignored) {
    }
    // once is OK
    AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx");
    AnnotationBuilder newBuilder = method.apply(builder);
    assertSame(newBuilder, builder);
    builder.create();
  }
  private static void checkThrowsWhenCalledTwiceOnFixBuilder(AnnotationHolder holder, Function<? super AnnotationBuilder.FixBuilder, ? extends AnnotationBuilder.FixBuilder> method) {
    IntentionAction fix = new IntentionAction() {
      @NotNull
      @Override
      public String getText() {
        return null;
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return null;
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return false;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {

      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
    try {
      AnnotationBuilder.FixBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx").newFix(fix);
      method.apply(builder);
      method.apply(builder);
      builder.registerFix().create();
      fail("Must have failed");
    }
    catch (IllegalStateException ignored) {
    }
    // once is OK
    AnnotationBuilder.FixBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "xxx").newFix(fix);
    AnnotationBuilder.FixBuilder newBuilder = method.apply(builder);
    assertSame(newBuilder, builder);
    builder.registerFix().create();
  }
  public static class MyStupidRepetitiveAnnotator extends DaemonRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        checkThrowsWhenCalledTwice(holder, builder -> builder.range(element.getTextRange()));
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
        checkThrowsWhenCalledTwice(holder, builder -> builder.needsUpdateOnTyping());
        checkThrowsWhenCalledTwice(holder, builder -> builder.problemGroup(() -> ""));
        checkThrowsWhenCalledTwice(holder, builder -> builder.tooltip(""));
        checkThrowsWhenCalledTwice(holder, builder -> builder.textAttributes(CodeInsightColors.DEPRECATED_ATTRIBUTES));
        checkThrowsWhenCalledTwiceOnFixBuilder(holder, fixBuilder -> fixBuilder.batch());
        HighlightDisplayKey myDeadCodeKey = HighlightDisplayKey.findOrRegister(UnusedDeclarationInspectionBase.SHORT_NAME, UnusedDeclarationInspectionBase.DISPLAY_NAME, UnusedDeclarationInspectionBase.SHORT_NAME);
        checkThrowsWhenCalledTwiceOnFixBuilder(holder, fixBuilder -> fixBuilder.key(myDeadCodeKey));
        checkThrowsWhenCalledTwiceOnFixBuilder(holder, fixBuilder -> fixBuilder.range(new TextRange(0,0)));
        iDidIt();
      }
    }
  }

  public void testAnnotationBuilderMethodsAllowedToBeCalledOnlyOnce() {
    DaemonRespondToChangesTest.useAnnotatorsIn(StdFileTypes.JAVA.getLanguage(), new DaemonRespondToChangesTest.MyRecordingAnnotator[]{new MyStupidRepetitiveAnnotator()}, () -> runMyAnnotators()
    );
  }
}