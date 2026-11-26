package com.intellij.java.injection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.intellij.java.injection.InjectedLanguageFixtureKt.injectedLanguageFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@ExtendWith(InjectedLanguageInvariantsExtension.class)
public class JavaSmartPointersInInjectedLanguageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = injectedLanguageFixture(projectFixture);

  private LightJavaCodeInsightTestFixture getFixture() {
    return daemonFixture.get().getFixture();
  }

  @Test
  public void testPointerToInjectedXmlAttribute() {
    daemonFixture.get().configureFromFileText("X.java", "class X { String xml=\"<xml attribute=\\\"va<caret>lue\\\"/>\"}");
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    ReadAction.runBlocking(() -> {
      final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      final XmlAttributeValue attributeValue = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlAttributeValue.class);
      assertNotNull(attributeValue);

      final SmartPointerManager smartPointerManager = getSPManager(myFixture.getProject());
      final SmartPsiElementPointer<XmlAttributeValue> pointer = smartPointerManager.createSmartPsiElementPointer(attributeValue);
      assertNotNull(pointer.getRange());
      assertEquals(attributeValue, pointer.getElement());
    });
  }

  @Test
  public void testPointerToInjectedXmlFileRange() {
    daemonFixture.get().configureFromFileText("X.java", "class X { String xml=\"<xml attribute=\\\"va<caret>lue\\\"/>\"}");
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    ReadAction.runBlocking(() -> {
      final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      final XmlAttributeValue xmlAttributeValue = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlAttributeValue.class);
      final XmlFile xmlFile = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlFile.class);
      assertNotNull(xmlFile);

      final SmartPointerManager smartPointerManager = getSPManager(myFixture.getProject());
      final SmartPsiFileRange pointer = smartPointerManager.createSmartPsiFileRangePointer(xmlFile, xmlAttributeValue.getTextRange());
      assertNotNull(pointer.getRange());
      assertEquals(xmlFile, pointer.getElement());
    });
  }

  @Test
  public void testRangeIsNotGreedy() {
    daemonFixture.get().configureFromFileText("X.java", "class X { String xml=\"<xml attribute=\\\"<caret>value\\\"/>\"}");
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      final XmlFile xmlFile = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlFile.class);
      assertNotNull(xmlFile);

      final SmartPointerManager smartPointerManager = getSPManager(myFixture.getProject());
      final TextRange range = elementAt.getTextRange();
      final SmartPsiFileRange pointer = smartPointerManager.createSmartPsiFileRangePointer(xmlFile, range);
      assertNotNull(pointer.getRange());
      assertEquals(xmlFile, pointer.getElement());

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> xmlFile.getViewProvider().getDocument().insertString(range.getStartOffset(), "a"));
      assertEquals(range.shiftRight(1), pointer.getRange());

      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      assertEquals(range.shiftRight(1), pointer.getRange());
    });
  }

  @Test
  public void testPointerToReferenceSurvivesRename() {
    daemonFixture.get().configureFromFileText("X.java", "class X { String java=\"class Foo \" + \"extends <caret>Bar{}\"}");
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      final PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), offset, PsiJavaCodeReferenceElement.class, false);

      SmartPsiElementPointer pointer = getSPManager(myFixture.getProject()).createSmartPsiElementPointer(ref);

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        ref.handleElementRename("BarImpl");
      });
      assertEquals(ref.getTextRange(), pointer.getRange());
      assertEquals(ref.getTextRange(), pointer.getPsiRange());
      assertSame(ref, pointer.getElement());

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        ref.handleElementRename("FooBarImpl");
      });
      assertEquals(ref.getTextRange(), pointer.getRange());
      assertEquals(ref.getTextRange(), pointer.getPsiRange());
      assertSame(ref, pointer.getElement());
    });
  }

  private static SmartPointerManagerImpl getSPManager(Project project) {
    return (SmartPointerManagerImpl)SmartPointerManager.getInstance(project);
  }

  @Test
  public void testReformatDoesntInvalidate() {
    daemonFixture.get().configureByFile("/injection/Reformat.java");
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      Project project = myFixture.getProject();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PsiFile file = myFixture.getFile();
      EditorWindow editor = (EditorWindow)myFixture.getEditor();
      assertTrue(InjectedLanguageManager.getInstance(project).isInjectedFragment(file));
      assertEquals(JavaLanguage.INSTANCE, file.getLanguage());
      assertTrue(file.isValid());
      assertTrue(editor.isValid());

      PsiFile top = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
      int i = top.getText().indexOf("\"  @Z Object x() {");
      PsiLiteralExpression injHost = PsiTreeUtil.getParentOfType(top.findElementAt(i), PsiLiteralExpression.class);
      SmartPsiElementPointer<PsiLiteralExpression> p = getSPManager(project).createSmartPsiElementPointer(injHost, top, true);
      assertNotNull(p.getElement());
      WriteCommandAction.runWriteCommandAction(project, ()-> myFixture.getDocument(top).insertString(i, " "));
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      assertNotNull(p.getElement());
      assertTrue(file.isValid());
      assertTrue(editor.isValid());
    });
  }

  @Test
  public void testPointerDoesNotResolveAfterItsInjectionHostIsRemoved() {
    daemonFixture.get().configureFromFileText("X.java", "class X { String xml=\"<xml attribute=\\\"va<caret>lue\\\"/>\"}");
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      Project project = myFixture.getProject();
      PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      XmlAttributeValue attributeValue = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlAttributeValue.class);
      assertNotNull(attributeValue);

      SmartPsiElementPointer<XmlAttributeValue> pointer = getSPManager(project).createSmartPsiElementPointer(attributeValue);
      assertNotNull(pointer.getElement());

      InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(project);
      PsiLanguageInjectionHost host = injectedManager.getInjectionHost(attributeValue.getContainingFile());
      assertNotNull(host);
      PsiFile hostFile = injectedManager.getTopLevelFile(attributeValue);
      TextRange hostRange = host.getTextRange();

      WriteCommandAction.runWriteCommandAction(project, () ->
        hostFile.getViewProvider().getDocument().replaceString(hostRange.getStartOffset(), hostRange.getEndOffset(), "y"));
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      assertNull(pointer.getElement(), "The pointer must not resolve after its injection host is removed");
      assertNull(pointer.getRange(), "The range must die with the injection");
    });
  }
}
