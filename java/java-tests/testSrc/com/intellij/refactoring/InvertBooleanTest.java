package com.intellij.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.invertBoolean.InvertBooleanProcessor;
import com.intellij.testFramework.TestDataPath;

/**
 * @author ven
 */
@TestDataPath("$CONTENT_ROOT/testData/refactoring/invertBoolean/")
public class InvertBooleanTest extends LightRefactoringParameterizedTestCase {
  @Override
  protected void perform() {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiNamedElement);

    final PsiNamedElement namedElement = (PsiNamedElement)element;
    final String name = namedElement.getName();
    new InvertBooleanProcessor(namedElement, name + "Inverted").run();
  }

  @Override
  protected String getAfterFile(String fileName) {
    return FileUtilRt.getNameWithoutExtension(fileName) + "_" + AFTER_PREFIX + "." + FileUtilRt.getExtension(fileName);
  }

  @Override
  protected String getBeforeFile(String fileName) {
    return fileName;
  }

  @Override
  public String getFileSuffix(String beforeFile) {
    return !beforeFile.contains(AFTER_PREFIX) && !beforeFile.endsWith(CONFLICTS_SUFFIX) ? beforeFile : null;
  }
}
