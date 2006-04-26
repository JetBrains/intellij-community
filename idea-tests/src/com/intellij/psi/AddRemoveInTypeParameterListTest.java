package com.intellij.psi;

import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;

public class AddRemoveInTypeParameterListTest extends LightIdeaTestCase{
  public void testAdd() throws IncorrectOperationException {
    PsiJavaFile file = (PsiJavaFile)createLightFile("Test.java", "class Test extends Type {\n}");
    PsiClass aClass = file.getClasses()[0];
    PsiJavaCodeReferenceElement ref = aClass.getExtendsList().getReferenceElements()[0];
    PsiReferenceParameterList list = ref.getParameterList();

    PsiElementFactory factory = getPsiManager().getElementFactory();

    PsiTypeElement typeElement1 = factory.createTypeElement(factory.createTypeFromText("A", null));
    list.add(typeElement1);

    assertEquals("class Test extends Type<A> {\n}", file.getText());
    PsiTestUtil.checkFileStructure(file);

    PsiTypeElement typeElement2 = factory.createTypeElement(factory.createTypeFromText("B", null));
    list.add(typeElement2);

    assertEquals("class Test extends Type<A, B> {\n}", file.getText());
    PsiTestUtil.checkFileStructure(file);

    PsiTypeElement typeElement3 = factory.createTypeElement(factory.createTypeFromText("C", null));
    list.addAfter(typeElement3, null);

    assertEquals("class Test extends Type<C, A, B> {\n}", file.getText());
    PsiTestUtil.checkFileStructure(file);
  }

  public void testRemove() throws IncorrectOperationException {
    PsiJavaFile file = (PsiJavaFile)createLightFile("Test.java", "class Test extends Type<A, B, C, D> {\n}");
    PsiClass aClass = file.getClasses()[0];
    PsiJavaCodeReferenceElement ref = aClass.getExtendsList().getReferenceElements()[0];
    PsiReferenceParameterList list = ref.getParameterList();
    PsiTypeElement[] parms = list.getTypeParameterElements();

    parms[0].delete();

    assertEquals("class Test extends Type< B, C, D> {\n}", file.getText());
    PsiTestUtil.checkFileStructure(file);

    parms[2].delete();

    assertEquals("class Test extends Type< B,  D> {\n}", file.getText());
    PsiTestUtil.checkFileStructure(file);

    parms[3].delete();

    assertEquals("class Test extends Type< B  > {\n}", file.getText());
    PsiTestUtil.checkFileStructure(file);

    parms[1].delete();

    assertEquals("class Test extends Type {\n}", file.getText());
    PsiTestUtil.checkFileStructure(file);
  }
}
