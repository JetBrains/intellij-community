package com.intellij.json.editor.selection;

import com.intellij.json.JsonParserDefinition;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;

/**
 * @author Mikhail Golubev
 */
public class JsonBasicWordSelectionFilter implements Condition<PsiElement> {
  @Override
  public boolean value(PsiElement element) {
    return !(JsonParserDefinition.STRING_LITERALS.contains(PsiUtilCore.getElementType(element)));
  }
}
