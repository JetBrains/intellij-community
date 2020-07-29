/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.java;

import com.google.common.base.MoreObjects;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.ApproximateResolver;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class FunExprOccurrence {
  private final int argIndex;
  private final List<? extends ReferenceChainLink> referenceContext;

  public FunExprOccurrence(int argIndex, List<? extends ReferenceChainLink> referenceContext) {
    this.argIndex = argIndex;
    this.referenceContext = referenceContext;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FunExprOccurrence)) return false;

    FunExprOccurrence that = (FunExprOccurrence)o;

    if (argIndex != that.argIndex) return false;
    if (!referenceContext.equals(that.referenceContext)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 31 * argIndex + referenceContext.hashCode();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("argIndex", argIndex)
      .add("chain", referenceContext)
      .toString();
  }

  void serialize(DataOutput out) throws IOException {
    DataInputOutputUtil.writeINT(out, argIndex);
    DataInputOutputUtilRt.writeSeq(out, referenceContext, link -> serializeLink(out, link));
  }

  static FunExprOccurrence deserialize(DataInput in) throws IOException {
    int argIndex = DataInputOutputUtil.readINT(in);
    return new FunExprOccurrence(argIndex, DataInputOutputUtilRt.readSeq(in, () -> deserializeLink(in)));
  }

  private static void serializeLink(DataOutput out, ReferenceChainLink link) throws IOException {
    IOUtil.writeUTF(out, link.referenceName);
    out.writeBoolean(link.isCall);
    if (link.isCall) {
      DataInputOutputUtil.writeINT(out, link.argCount);
    }
  }

  @NotNull
  private static ReferenceChainLink deserializeLink(DataInput in) throws IOException {
    String referenceName = IOUtil.readUTF(in);
    boolean isCall = in.readBoolean();
    return new ReferenceChainLink(referenceName, isCall, isCall ? DataInputOutputUtil.readINT(in) : -1);
  }

  public ThreeState checkHasTypeLight(@NotNull List<? extends PsiClass> samClasses, @NotNull VirtualFile placeFile) {
    if (referenceContext.isEmpty()) return ThreeState.UNSURE;

    Set<PsiClass> qualifiers = null;
    int maxPossiblePackageComponent = referenceContext.size() - 2;
    for (int i = 0; i < referenceContext.size(); i++) {
      if (referenceContext.get(i).isCall) {
        maxPossiblePackageComponent = i - 2;
        break;
      }
    }
    for (int i = 0; i < referenceContext.size(); i++) {
      ReferenceChainLink link = referenceContext.get(i);
      if (qualifiers == null && i > 0 && i <= maxPossiblePackageComponent) {
        // probably fully qualified name: skip to possible class name (right before the first call)
        continue;
      }
      List<? extends PsiMember> candidates = qualifiers == null ? link.getGlobalMembers(placeFile, samClasses.get(0).getProject())
                                                                : link.getSymbolMembers(qualifiers);
      if (candidates == null) {
        continue;
      }

      if (i == referenceContext.size() - 1) {
        return candidates.isEmpty() ? ThreeState.NO :
               ThreeState.merge(JBIterable.from(candidates).map(m -> isCompatible(link, m, samClasses)));
      }
      qualifiers = ApproximateResolver.getDefiniteSymbolTypes(candidates, qualifiers != null ? qualifiers : Collections.emptySet());
    }

    return ThreeState.UNSURE;
  }

  private ThreeState isCompatible(ReferenceChainLink link, PsiMember member, List<? extends PsiClass> samClasses) {
    if (link.isCall) {
      return member instanceof PsiMethod && argIndex >= 0 ? hasCompatibleParameter((PsiMethod)member, argIndex, samClasses) : ThreeState.NO;
    }
    if (member instanceof PsiClass) {
      return ThreeState.fromBoolean(samClasses.contains(member));
    }
    return member instanceof PsiField ? canPassFunctionalExpression(samClasses, ((PsiField)member).getType(), member) : ThreeState.NO;
  }

  public static ThreeState hasCompatibleParameter(PsiMethod method, int argIndex, List<? extends PsiClass> samClasses) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    int paramIndex = method.isVarArgs() ? Math.min(argIndex, parameters.length - 1) : argIndex;
    return paramIndex < parameters.length ? canPassFunctionalExpression(samClasses, parameters[paramIndex].getType(), method) : ThreeState.NO;
  }

  private static ThreeState canPassFunctionalExpression(List<? extends PsiClass> samClasses, PsiType type, PsiElement place) {
    return ThreeState.mostPositive(JBIterable.from(samClasses).map(c -> canPassFunctionalExpression(c, type, place)));
  }

  private static ThreeState canPassFunctionalExpression(PsiClass sam, PsiType paramType, PsiElement place) {
    if (paramType instanceof PsiEllipsisType) {
      paramType = ((PsiEllipsisType)paramType).getComponentType();
    }
    String paramClassName = paramType instanceof PsiClassType ? ((PsiClassType)paramType).getClassName() : null;
    if (paramClassName == null) return ThreeState.NO;

    if (paramClassName.equals(sam.getName()) && sam.getManager().areElementsEquivalent(sam, ((PsiClassType)paramType).resolve())) {
      return ThreeState.YES;
    }

    if (isTypeParameterVisible(paramClassName, place) &&
        ((PsiClassType)paramType).resolve() instanceof PsiTypeParameter &&
        hasSuperTypeAssignableFromSam(sam, paramType)) {
      return ThreeState.UNSURE;
    }
    return ThreeState.NO;
  }

  private static boolean isTypeParameterVisible(String name, PsiElement fromPlace) {
    JBIterable<String> typeParameters = JBIterable.generate(fromPlace, PsiElement::getContext)
      .takeWhile(c -> !(c instanceof PsiFile))
      .filter(PsiTypeParameterListOwner.class)
      .flatMap(o -> Arrays.asList(o.getTypeParameters()))
      .map(PsiNamedElement::getName);
    return typeParameters.contains(name);
  }

  private static boolean hasSuperTypeAssignableFromSam(PsiClass sam, PsiType type) {
    return !InheritanceUtil.processSuperTypes(type, false, superType ->
      !InheritanceUtil.isInheritorOrSelf(sam, PsiUtil.resolveClassInClassTypeOnly(superType), true));
  }
}
