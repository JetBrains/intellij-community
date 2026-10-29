package com.intellij.java.injection;

import com.intellij.JavaTestUtil;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
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
import org.jetbrains.annotations.NotNull;

public class JavaSmartPointersInInjectedLanguageTest extends InjectedLanguageTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }


  public void testPointerToInjectedXmlAttribute() {
    configureFromFileText("X.java", "class X { String xml=\"<xml attribute=\\\"va<caret>lue\\\"/>\"}");
    final PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    final XmlAttributeValue attributeValue = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlAttributeValue.class);
    assertNotNull(attributeValue);

    final SmartPointerManager smartPointerManager = getSPManager();
    final SmartPsiElementPointer<XmlAttributeValue> pointer = smartPointerManager.createSmartPsiElementPointer(attributeValue);
    assertNotNull(pointer.getRange());
    assertEquals(attributeValue, pointer.getElement());
  }

  public void testPointerToInjectedXmlFileRange() {
    configureFromFileText("X.java", "class X { String xml=\"<xml attribute=\\\"va<caret>lue\\\"/>\"}");
    final PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    final XmlAttributeValue xmlAttributeValue = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlAttributeValue.class);
    final XmlFile xmlFile = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlFile.class);
    assertNotNull(xmlFile);

    final SmartPointerManager smartPointerManager = getSPManager();
    final SmartPsiFileRange pointer = smartPointerManager.createSmartPsiFileRangePointer(xmlFile, xmlAttributeValue.getTextRange());
    assertNotNull(pointer.getRange());
    assertEquals(xmlFile, pointer.getElement());
  }

  public void testRangeIsNotGreedy() {
    configureFromFileText("X.java", "class X { String xml=\"<xml attribute=\\\"<caret>value\\\"/>\"}");
    final PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    final XmlFile xmlFile = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlFile.class);
    assertNotNull(xmlFile);

    final SmartPointerManager smartPointerManager = getSPManager();
    final TextRange range = elementAt.getTextRange();
    final SmartPsiFileRange pointer = smartPointerManager.createSmartPsiFileRangePointer(xmlFile, range);
    assertNotNull(pointer.getRange());
    assertEquals(xmlFile, pointer.getElement());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> xmlFile.getViewProvider().getDocument().insertString(range.getStartOffset(), "a"));
    assertEquals(range.shiftRight(1), pointer.getRange());

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEquals(range.shiftRight(1), pointer.getRange());
  }

  public void testPointerToReferenceSurvivesRename() {
    configureFromFileText("X.java", "class X { String java=\"class Foo \" + \"extends <caret>Bar{}\"}");
    int offset = getEditor().getCaretModel().getOffset();
    final PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(getFile(), offset, PsiJavaCodeReferenceElement.class, false);

    SmartPsiElementPointer pointer = getSPManager().createSmartPsiElementPointer(ref);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      ref.handleElementRename("BarImpl");
    });
    assertEquals(ref.getTextRange(), pointer.getRange());
    assertEquals(ref.getTextRange(), pointer.getPsiRange());
    assertSame(ref, pointer.getElement());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      ref.handleElementRename("FooBarImpl");
    });
    assertEquals(ref.getTextRange(), pointer.getRange());
    assertEquals(ref.getTextRange(), pointer.getPsiRange());
    assertSame(ref, pointer.getElement());
  }

  private SmartPointerManagerImpl getSPManager() {
    return (SmartPointerManagerImpl)SmartPointerManager.getInstance(getProject());
  }

  public void testReformatDoesntInvalidate() {
    configureByFile("/injection/Reformat.java");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertTrue(InjectedLanguageManager.getInstance(getProject()).isInjectedFragment(getFile()));
    assertEquals(JavaLanguage.INSTANCE, getFile().getLanguage());
    assertTrue(getFile().isValid());
    assertTrue(((EditorWindow)getEditor()).isValid());

    PsiFile top = InjectedLanguageManager.getInstance(getProject()).getTopLevelFile(getFile());
    int i = top.getText().indexOf("\"  @Z Object x() {");
    PsiLiteralExpression injHost = PsiTreeUtil.getParentOfType(top.findElementAt(i), PsiLiteralExpression.class);
    SmartPsiElementPointer<PsiLiteralExpression> p = getSPManager().createSmartPsiElementPointer(injHost, top, true);
    assertNotNull(p.getElement());
    WriteCommandAction.runWriteCommandAction(getProject(), ()-> getDocument(top).insertString(i, " "));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertNotNull(p.getElement());
    assertTrue(getFile().isValid());
    assertTrue(((EditorWindow)getEditor()).isValid());
  }

  public void testPointerDoesNotResolveAfterItsInjectionHostIsRemoved() {
    configureFromFileText("X.java", "class X { String xml=\"<xml attribute=\\\"va<caret>lue\\\"/>\"}");
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    XmlAttributeValue attributeValue = PsiTreeUtil.getNonStrictParentOfType(elementAt, XmlAttributeValue.class);
    assertNotNull(attributeValue);

    SmartPsiElementPointer<XmlAttributeValue> pointer = getSPManager().createSmartPsiElementPointer(attributeValue);
    assertNotNull(pointer.getElement());

    InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(getProject());
    PsiLanguageInjectionHost host = injectedManager.getInjectionHost(attributeValue.getContainingFile());
    assertNotNull(host);
    PsiFile hostFile = injectedManager.getTopLevelFile(attributeValue);
    TextRange hostRange = host.getTextRange();

    WriteCommandAction.runWriteCommandAction(getProject(), () ->
      hostFile.getViewProvider().getDocument().replaceString(hostRange.getStartOffset(), hostRange.getEndOffset(), "y"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertNull("The pointer must not resolve after its injection host is removed", pointer.getElement());
    assertNull("The range must die with the injection", pointer.getRange());
  }
}
