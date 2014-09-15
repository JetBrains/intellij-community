package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonNamePropertyReference implements PsiReference {
  private final JsonProperty myProperty;

  public JsonNamePropertyReference(@NotNull JsonProperty property) {
    myProperty = property;
  }

  @Override
  public PsiElement getElement() {
    return myProperty;
  }

  @Override
  public TextRange getRangeInElement() {
    final JsonValue nameElement = myProperty.getNameElement();
    final String nameText = nameElement.getText();
    final int startOffset = nameText.startsWith("\"") || nameText.startsWith("'")? 1 : 0;
    final int endOffset = nameText.length() > 1 && (nameText.endsWith("\"") || nameText.endsWith("'")) ? -1 : 0;
    return new TextRange(nameElement.getStartOffsetInParent() + startOffset,
                         nameElement.getStartOffsetInParent() + nameElement.getTextLength() + endOffset);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myProperty;
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return myProperty.getName();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return myProperty.setName(newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof JsonProperty)) {
      return false;
    }
    final JsonProperty otherProperty = (JsonProperty)element;
    final PsiElement selfResolve = resolve();
    return otherProperty.getName().equals(getCanonicalText()) && selfResolve != otherProperty;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}
