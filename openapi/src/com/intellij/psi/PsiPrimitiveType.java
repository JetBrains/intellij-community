/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 17, 2003
 * Time: 4:25:54 PM
 * To change this template use Options | File Templates.
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashMap;

import java.util.Map;

/**
 * Represents primitive types of Java language
 */
public class PsiPrimitiveType extends PsiType {
  protected static final PsiPrimitiveType VOID = new PsiPrimitiveType("void");
  protected static final PsiPrimitiveType BYTE = new PsiPrimitiveType("byte");
  protected static final PsiPrimitiveType CHAR = new PsiPrimitiveType("char");
  protected static final PsiPrimitiveType DOUBLE = new PsiPrimitiveType("double");
  protected static final PsiPrimitiveType FLOAT = new PsiPrimitiveType("float");
  protected static final PsiPrimitiveType LONG = new PsiPrimitiveType("long");
  protected static final PsiPrimitiveType INT = new PsiPrimitiveType("int");
  protected static final PsiPrimitiveType SHORT = new PsiPrimitiveType("short");
  protected static final PsiPrimitiveType BOOLEAN = new PsiPrimitiveType("boolean");
  protected static final PsiPrimitiveType NULL = new PsiPrimitiveType("null");

  private final String myName;

  private PsiPrimitiveType(String name) {
    myName = name;
  }

  public String getPresentableText() {
    return myName;
  }

  public String getCanonicalText() {
    return myName;
  }

  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  /**
   * Always returns true.
   */
  public boolean isValid() {
    return true;
  }

  public boolean equalsToText(String text) {
    return myName.equals(text);
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitPrimitiveType(this);
  }

  public GlobalSearchScope getResolveScope() {
    return null;
  }

  public PsiType[] getSuperTypes() {
    return new PsiType[0];
  }

  public static PsiPrimitiveType getUnboxedType(PsiType type) {
    if (!(type instanceof PsiClassType)) return null;
    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return null;
    if (!psiClass.getManager().getEffectiveLanguageLevel().hasEnumKeywordAndAutoboxing()) return null;
    return ourQNameToUnboxed.get(psiClass.getQualifiedName());
  }

  public PsiClassType getBoxedType(PsiManager manager, GlobalSearchScope resolveScope) {
    if (!manager.getEffectiveLanguageLevel().hasEnumKeywordAndAutoboxing()) return null;
    final String boxedQName = ourUnboxedToQName.get(this);

    //[ven]previous call returns null for NULL, VOID
    if (boxedQName == null) return null;

    final PsiClass aClass = manager.findClass(boxedQName, resolveScope);
    if (aClass == null) return null;
    return manager.getElementFactory().createType(aClass);
  }

  private static final Map<String, PsiPrimitiveType> ourQNameToUnboxed = new HashMap<String, PsiPrimitiveType>();
  private static final Map<PsiPrimitiveType, String> ourUnboxedToQName = new HashMap<PsiPrimitiveType, String>();
  static {
    ourQNameToUnboxed.put("java.lang.Boolean", BOOLEAN);
    ourUnboxedToQName.put(BOOLEAN, "java.lang.Boolean");
    ourQNameToUnboxed.put("java.lang.Byte", BYTE);
    ourUnboxedToQName.put(BYTE, "java.lang.Byte");
    ourQNameToUnboxed.put("java.lang.Character", CHAR);
    ourUnboxedToQName.put(CHAR, "java.lang.Character");
    ourQNameToUnboxed.put("java.lang.Short", SHORT);
    ourUnboxedToQName.put(SHORT, "java.lang.Short");
    ourQNameToUnboxed.put("java.lang.Integer", INT);
    ourUnboxedToQName.put(INT, "java.lang.Integer");
    ourQNameToUnboxed.put("java.lang.Long", LONG);
    ourUnboxedToQName.put(LONG, "java.lang.Long");
    ourQNameToUnboxed.put("java.lang.Float", FLOAT);
    ourUnboxedToQName.put(FLOAT, "java.lang.Float");
    ourQNameToUnboxed.put("java.lang.Double", DOUBLE);
    ourUnboxedToQName.put(DOUBLE, "java.lang.Double");
  }
}
