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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.ApproximateResolver;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class FunExprOccurrence {
  public final int funExprOffset;
  private final int argIndex;
  private final List<ReferenceChainLink> referenceContext;

  public FunExprOccurrence(int funExprOffset,
                           int argIndex,
                           List<ReferenceChainLink> referenceContext) {
    this.funExprOffset = funExprOffset;
    this.argIndex = argIndex;
    this.referenceContext = referenceContext;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FunExprOccurrence)) return false;

    FunExprOccurrence that = (FunExprOccurrence)o;

    if (funExprOffset != that.funExprOffset) return false;
    if (argIndex != that.argIndex) return false;
    if (!referenceContext.equals(that.referenceContext)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = funExprOffset;
    result = 31 * result + argIndex;
    result = 31 * result + referenceContext.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("offset", funExprOffset)
      .add("argIndex", argIndex)
      .add("chain", referenceContext)
      .toString();
  }

  void serialize(DataOutput out) throws IOException {
    DataInputOutputUtil.writeINT(out, funExprOffset);
    DataInputOutputUtil.writeINT(out, argIndex);

    DataInputOutputUtil.writeINT(out, referenceContext.size());
    for (ReferenceChainLink link : referenceContext) {
      serializeLink(out, link);
    }
  }

  static FunExprOccurrence deserialize(DataInput in) throws IOException {
    int offset = DataInputOutputUtil.readINT(in);
    int argIndex = DataInputOutputUtil.readINT(in);

    int contextSize = DataInputOutputUtil.readINT(in);
    List<ReferenceChainLink> context = new ArrayList<>(contextSize);
    for (int i = 0; i < contextSize; i++) {
      context.add(deserializeLink(in));
    }
    return new FunExprOccurrence(offset, argIndex, context);
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

  public boolean canHaveType(@NotNull List<PsiClass> samClasses, @NotNull VirtualFile placeFile) {
    if (referenceContext.isEmpty()) return true;

    Set<PsiClass> qualifiers = null;
    for (int i = 0; i < referenceContext.size(); i++) {
      ReferenceChainLink link = referenceContext.get(i);
      List<? extends PsiMember> candidates = i == 0 ? link.getGlobalMembers(placeFile, samClasses.get(0).getProject())
                                                    : link.getSymbolMembers(qualifiers);
      if (candidates == null) return true;

      if (i == referenceContext.size() - 1) {
        return ContainerUtil.exists(candidates, m -> isCompatible(link, m, samClasses));
      }
      qualifiers = ApproximateResolver.getDefiniteSymbolTypes(candidates);
      if (qualifiers == null) return true;
    }

    return true;
  }

  private boolean isCompatible(ReferenceChainLink link, PsiMember member, List<PsiClass> samClasses) {
    if (link.isCall) {
      return member instanceof PsiMethod && hasCompatibleParameter((PsiMethod)member, argIndex, samClasses);
    }
    if (member instanceof PsiClass) {
      return ContainerUtil.exists(samClasses, c -> InheritanceUtil.isInheritorOrSelf((PsiClass)member, c, true));
    }
    return member instanceof PsiField &&
           ContainerUtil.exists(samClasses, c -> canPassFunctionalExpression(c, ((PsiField)member).getType()));
  }

  public static boolean hasCompatibleParameter(PsiMethod method, int argIndex, List<PsiClass> samClasses) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    int paramIndex = method.isVarArgs() ? Math.min(argIndex, parameters.length - 1) : argIndex;
    return paramIndex < parameters.length &&
           ContainerUtil.exists(samClasses, c -> canPassFunctionalExpression(c, parameters[paramIndex].getType()));
  }

  private static boolean canPassFunctionalExpression(PsiClass sam, PsiType paramType) {
    if (paramType instanceof PsiEllipsisType) {
      paramType = ((PsiEllipsisType)paramType).getComponentType();
    }
    PsiClass functionalCandidate = PsiUtil.resolveClassInClassTypeOnly(paramType);
    if (functionalCandidate instanceof PsiTypeParameter) {
      return InheritanceUtil.isInheritorOrSelf(sam, PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(paramType)), true);
    }

    return InheritanceUtil.isInheritorOrSelf(functionalCandidate, sam, true);
  }
}
