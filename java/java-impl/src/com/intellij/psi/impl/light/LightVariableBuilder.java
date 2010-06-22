package com.intellij.psi.impl.light;

import com.intellij.lang.StdLanguages;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author peter
 */
public class LightVariableBuilder extends LightElement implements PsiVariable {
  private final String myName;
  private final PsiType myType;
  private volatile LightModifierList myModifierList;
  private volatile Icon myBaseIcon = Icons.VARIABLE_ICON;

  public LightVariableBuilder(@NotNull String name, @NotNull String type, @NotNull PsiElement navigationElement) {
    this(name, JavaPsiFacade.getElementFactory(navigationElement.getProject()).createTypeFromText(type, navigationElement), navigationElement);
  }

  public LightVariableBuilder(@NotNull String name, @NotNull PsiType type, @NotNull PsiElement navigationElement) {
    this(navigationElement.getManager(), name, type);
    setNavigationElement(navigationElement);
  }
  
  public LightVariableBuilder(PsiManager manager, @NotNull String name, @NotNull PsiType type) {
    super(manager, StdLanguages.JAVA);
    myName = name;
    myType = type;
    myModifierList = new LightModifierList(manager);
  }

  @Override
  public String toString() {
    return "LightVariableBuilder:" + getName();
  }

  @NotNull
  @Override
  public PsiType getType() {
    return myType;
  }

  @Override
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  public LightVariableBuilder setModifiers(String... modifiers) {
    myModifierList = new LightModifierList(getManager(), getLanguage(), modifiers);
    return this;
  }

  @Override
  public boolean hasModifierProperty(@Modifier @NonNls @NotNull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return null;
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("setName is not implemented yet in com.intellij.psi.impl.light.LightVariableBuilder");
  }

  @Override
  public PsiType getTypeNoResolve() {
    return getType();
  }

  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = createLayeredIcon(myBaseIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  public LightVariableBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }


}
