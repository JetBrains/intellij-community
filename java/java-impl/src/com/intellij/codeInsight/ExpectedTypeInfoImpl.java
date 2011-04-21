
/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ExpectedTypeInfoImpl implements ExpectedTypeInfo {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ExpectedTypeInfoImpl");

  private final PsiType type;
  private final PsiType defaultType;
  private boolean myInsertExplicitTypeParams;

  int getDimCount() {
    return dimCount;
  }

  private final int dimCount;

  public int getKind() {
    return kind;
  }

  public int kind;

  public TailType getTailType() {
    return myTailType;
  }

  public TailType myTailType;

  public String expectedName;
  private PsiMethod myCalledMethod;


  public ExpectedTypeInfoImpl(@NotNull PsiType type, int kind, int dimCount, @NotNull PsiType defaultType, @NotNull TailType myTailType) {
    this.type = type;
    this.kind = kind;

    this.myTailType = myTailType;
    this.dimCount = dimCount;

    if (type == defaultType && type instanceof PsiClassType) {
      final PsiClassType psiClassType = (PsiClassType)type;
      final PsiClass psiClass = psiClassType.resolve();
      if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
        final PsiType[] parameters = psiClassType.getParameters();
        if (parameters.length == 1 && parameters[0] instanceof PsiWildcardType) {
          final PsiType bound = ((PsiWildcardType)parameters[0]).getExtendsBound();
          if (bound instanceof PsiClassType) {
            final PsiElementFactory factory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
            String canonicalText = bound.getCanonicalText();
            if (canonicalText.contains("?extends")) {
              throw new AssertionError("Incorrect text: " + bound + "; " + Arrays.toString(((PsiClassType)bound).getParameters()));
            }

            defaultType = factory.createTypeFromText(CommonClassNames.JAVA_LANG_CLASS + "<" + canonicalText + ">", null);
          }
        }
      }
    }

    this.defaultType = defaultType;
  }

  public PsiMethod getCalledMethod() {
    return myCalledMethod;
  }

  public void setCalledMethod(final PsiMethod calledMethod) {
    myCalledMethod = calledMethod;
  }

  @NotNull
  public PsiType getType () {
    PsiType t = type;
    int dims = dimCount;

    while (dims-- > 0) t = t.createArrayType();
    return t;
  }

  @NotNull
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

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ExpectedTypeInfoImpl)) return false;

    final ExpectedTypeInfoImpl that = (ExpectedTypeInfoImpl)o;

    if (dimCount != that.dimCount) return false;
    if (kind != that.kind) return false;
    if (defaultType != null ? !defaultType.equals(that.defaultType) : that.defaultType != null) return false;
    if (myTailType != null ? !myTailType.equals(that.myTailType) : that.myTailType != null) return false;
    if (type != null ? !type.equals(that.type) : that.type != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (type != null ? type.hashCode() : 0);
    result = 31 * result + (defaultType != null ? defaultType.hashCode() : 0);
    result = 31 * result + dimCount;
    result = 31 * result + kind;
    result = 31 * result + (myTailType != null ? myTailType.hashCode() : 0);
    return result;
  }

  public boolean equals(ExpectedTypeInfo obj) {
    return equals((Object)obj);
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
        if (dimCount != info1.dimCount) return ExpectedTypeInfo.EMPTY_ARRAY;
        if (info1.type.equals(type)) return new ExpectedTypeInfoImpl[] {this};
      }
      else {
        return info1.intersect(this);
      }
    }
    else if (kind == TYPE_OR_SUBTYPE) {
      if (info1.kind == TYPE_STRICTLY) {
        if (dimCount != info1.dimCount) return ExpectedTypeInfo.EMPTY_ARRAY;
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
        if (dimCount != info1.dimCount) return ExpectedTypeInfo.EMPTY_ARRAY;
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

    return ExpectedTypeInfo.EMPTY_ARRAY;
  }

  public boolean isArrayTypeInfo () {
    return dimCount > 0;
  }

}
