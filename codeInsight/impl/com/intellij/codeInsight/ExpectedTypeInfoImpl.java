
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public class ExpectedTypeInfoImpl implements ExpectedTypeInfo {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ExpectedTypeInfoImpl");

  private PsiType type;
  private PsiType defaultType;
  private boolean myInsertExplicitTypeParams;

  int getDimCount() {
    return dimCount;
  }

  private int dimCount;

  public int getKind() {
    return kind;
  }

  public int kind;

  public TailType getTailType() {
    return myTailType;
  }

  public TailType myTailType;

  public String expectedName;


  ExpectedTypeInfoImpl(@NotNull PsiType type, int kind, int dimCount, @NotNull PsiType defaultType, @NotNull TailType myTailType) {
    this.type = type;
    this.kind = kind;
    this.defaultType = defaultType;
    this.myTailType = myTailType;
    this.dimCount = dimCount;
  }

  @NotNull
  public PsiType getType () {
    PsiType t = type;
    int dims = dimCount;

    while (dims-- > 0) t = t.createArrayType();
    return t;
  }

  public PsiType getDefaultType () {
    PsiType t = defaultType;
    int dims = dimCount;

    while (dims-- > 0) t = t.createArrayType();
    return t;
  }

  public boolean isInsertExplicitTypeParams() {
    return myInsertExplicitTypeParams;
  }

  public void setInsertExplicitTypeParams(final boolean insertExplicitTypeParams) {
    this.myInsertExplicitTypeParams = insertExplicitTypeParams;
  }

  public boolean equals (ExpectedTypeInfo obj) {
    ExpectedTypeInfoImpl info = (ExpectedTypeInfoImpl) obj;
    return type.equals(info.type) && dimCount == info.dimCount && kind == info.kind && defaultType.equals(info.defaultType) && myTailType == info.myTailType;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "ExpectedTypeInfo[type='" + type + "' kind='" + kind + "' dims='" + dimCount+ "']";
  }

  public ExpectedTypeInfo[] intersect(ExpectedTypeInfo info) {
    ExpectedTypeInfoImpl info1 = (ExpectedTypeInfoImpl)info;
    LOG.assertTrue(!(type instanceof PsiArrayType) && !(info1.type instanceof PsiArrayType));

    if (kind == TYPE_STRICTLY) {
      if (info1.kind == TYPE_STRICTLY) {
        if (dimCount != info1.dimCount) return ExpectedTypeInfo.EMPTY;
        if (info1.type.equals(type)) return new ExpectedTypeInfoImpl[] {this};
      }
      else {
        return info1.intersect(this);
      }
    }
    else if (kind == TYPE_OR_SUBTYPE) {
      if (info1.kind == TYPE_STRICTLY) {
        if (dimCount != info1.dimCount) return ExpectedTypeInfo.EMPTY;
        if (type.isAssignableFrom(info1.type)) return new ExpectedTypeInfoImpl[] {info1};
      }
      else if (info1.kind == TYPE_OR_SUBTYPE) {
        PsiType type = dimCount == info1.dimCount ? this.type : getType();
        PsiType otherType = dimCount == info1.dimCount ? info1.type : info1.getType();
        if (type.isAssignableFrom(otherType)) return new ExpectedTypeInfoImpl[] {info1};
        else if (otherType.isAssignableFrom(type)) return new ExpectedTypeInfoImpl[] {this};
      }
      else {
        return info1.intersect(this);
      }
    }
    else if (kind == TYPE_OR_SUPERTYPE) {
      if (info1.kind == TYPE_STRICTLY) {
        if (dimCount != info1.dimCount) return ExpectedTypeInfo.EMPTY;
        if (info1.type.isAssignableFrom(type)) return new ExpectedTypeInfoImpl[] {info1};
      }
      else if (info1.kind == TYPE_OR_SUBTYPE) {
        PsiType type = dimCount == info1.dimCount ? this.type : getType();
        PsiType otherType = dimCount == info1.dimCount ? info1.type : info1.getType();
        if (otherType.isAssignableFrom(type)) return new ExpectedTypeInfoImpl[] {this};
      }
      else if (info1.kind == TYPE_OR_SUPERTYPE) {
        PsiType type = dimCount == info1.dimCount ? this.type : getType();
        PsiType otherType = dimCount == info1.dimCount ? info1.type : info1.getType();
        if (type.isAssignableFrom(otherType)) return new ExpectedTypeInfoImpl[] {this};
        else if (otherType.isAssignableFrom(type)) return new ExpectedTypeInfoImpl[] {info1};
      }
      else {
        return info1.intersect(this);
      }
    }


    //todo: the following cases are not implemented: SUPERxSUB, SUBxSUPER

    return ExpectedTypeInfo.EMPTY;
  }

  public boolean isArrayTypeInfo () {
    return dimCount > 0;
  }

}