package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonPropertyNameReference implements PsiReference {
  private final JsonProperty myProperty;

  public JsonPropertyNameReference(@NotNull JsonProperty property) {
    myProperty = property;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myProperty;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    final JsonValue nameElement = myProperty.getNameElement();
    // Either value of string with quotes stripped or element's text as is
    return ElementManipulators.getValueTextRange(nameElement);
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
    // May reference to the property with the same name for compatibility with JavaScript JSON support
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
