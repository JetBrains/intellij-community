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
package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author ik, dsl
 */
public class PsiSubstitutorImpl implements PsiSubstitutor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiSubstitutorImpl");
  private final Map<PsiTypeParameter, PsiType> mySubstitutionMap;
  private final TObjectHashingStrategy<PsiTypeParameter> PSI_EQUIVALENCE = new TObjectHashingStrategy<PsiTypeParameter>() {
    public int computeHashCode(PsiTypeParameter object) {
      String name = object.getName();
      return name == null ? 0 : name.hashCode();
    }
    public boolean equals(PsiTypeParameter element1, PsiTypeParameter element2) {
      return element1.getManager().areElementsEquivalent(element1, element2);
    }
  };

  private PsiSubstitutorImpl(@NotNull Map<PsiTypeParameter, PsiType> map) {
    mySubstitutionMap = new THashMap<PsiTypeParameter, PsiType>(map, PSI_EQUIVALENCE);
  }

  PsiSubstitutorImpl() {
    mySubstitutionMap = new THashMap<PsiTypeParameter, PsiType>(2, PSI_EQUIVALENCE);
  }

  public PsiType substitute(@NotNull PsiTypeParameter typeParameter){
    if(!mySubstitutionMap.containsKey(typeParameter)){
      return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter);
    }
    return mySubstitutionMap.get(typeParameter);
  }

  public PsiType substitute(PsiType type) {
    if (type == null) return null;
    PsiType substituted = type.accept(myAddingBoundsSubstitutionVisitor);
    return correctExternalSubstitution(substituted, type);
  }

  public PsiType substituteWithBoundsPromotion(PsiTypeParameter typeParameter) {
    return addBounds(substitute(typeParameter), typeParameter);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof PsiSubstitutorImpl)) return false;

    final PsiSubstitutorImpl that = (PsiSubstitutorImpl)o;

    if (mySubstitutionMap != null ? !mySubstitutionMap.equals(that.mySubstitutionMap) : that.mySubstitutionMap != null) return false;

    return true;
  }

  public int hashCode() {
    return mySubstitutionMap != null ? mySubstitutionMap.hashCode() : 0;
  }

  private PsiType rawTypeForTypeParameter(final PsiTypeParameter typeParameter) {
    final PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
    if (extendsTypes.length > 0) {
      // First bound
      return substitute(extendsTypes[0]);
    }
    else {
      // Object
      return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
    }
  }

  private abstract static class SubstitutionVisitorBase extends PsiTypeVisitorEx<PsiType> {
    public PsiType visitType(PsiType type) {
      LOG.assertTrue(false);
      return null;
    }

    public PsiType visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      if (bound == null) {
        return wildcardType;
      }
      else {
        final PsiType newBound = bound.accept(this);
        if (newBound == null) {
          return null;
        }
        if (newBound instanceof PsiWildcardType) {
          return handleBoundComposition(wildcardType, (PsiWildcardType)newBound);
        }
        if (newBound instanceof PsiCapturedWildcardType && wildcardType.isExtends() != ((PsiCapturedWildcardType)newBound).getWildcard().isExtends()) {
          return handleBoundComposition(wildcardType, ((PsiCapturedWildcardType)newBound).getWildcard());
        }

        return PsiWildcardType.changeBound(wildcardType, newBound);
      }
    }

    private static PsiType handleBoundComposition(PsiWildcardType wildcardType, PsiWildcardType bound) {
      if (bound.isExtends() == wildcardType.isExtends()) {
        final PsiType newBoundBound = bound.getBound();
        if (newBoundBound != null) {
          return PsiWildcardType.changeBound(wildcardType, newBoundBound);
        }
      }
      return PsiWildcardType.createUnbounded(wildcardType.getManager());
    }

    public PsiType visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return primitiveType;
    }

    public PsiType visitArrayType(PsiArrayType arrayType) {
      final PsiType componentType = arrayType.getComponentType();
      final PsiType substitutedComponentType = componentType.accept(this);
      if (substitutedComponentType == null) return null;
      if (substitutedComponentType == componentType) return arrayType; // optimization
      return new PsiArrayType(substitutedComponentType);
    }

    public PsiType visitEllipsisType(PsiEllipsisType ellipsisType) {
      final PsiType componentType = ellipsisType.getComponentType();
      final PsiType substitutedComponentType = componentType.accept(this);
      if (substitutedComponentType == null) return null;
      if (substitutedComponentType == componentType) return ellipsisType; // optimization
      return new PsiEllipsisType(substitutedComponentType);
    }

    public PsiType visitTypeVariable(final PsiTypeVariable var) {
      return var;
    }

    public PsiType visitBottom(final Bottom bottom) {
      return bottom;
    }

    public abstract PsiType visitClassType(PsiClassType classType);
  }

  private final SubstitutionVisitor myAddingBoundsSubstitutionVisitor = new SubstitutionVisitor(SubstituteKind.ADD_BOUNDS);
  private final SubstitutionVisitor mySimpleSubstitutionVisitor = new SubstitutionVisitor(SubstituteKind.SIMPLE);

  enum SubstituteKind {
    SIMPLE,
    ADD_BOUNDS
  }

  private class SubstitutionVisitor extends SubstitutionVisitorBase {
    private SubstitutionVisitor(final SubstituteKind kind) {
      myKind = kind;
    }

    private final SubstituteKind myKind;

    public PsiType visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return classType;
      if (aClass instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter = (PsiTypeParameter)aClass;
        if (mySubstitutionMap.containsKey(typeParameter)) {
          return substituteTypeParameter(typeParameter);
        }
        else {
          return classType;
        }
      }
      final Map<PsiTypeParameter, PsiType> hashMap = new HashMap<PsiTypeParameter, PsiType>(2);
      if (!processClass(aClass, resolveResult.getSubstitutor(), hashMap)) {
        return null;
      }
      return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory()
        .createType(aClass, createSubstitutor(hashMap), classType.getLanguageLevel());
    }

    private PsiType substituteTypeParameter(final PsiTypeParameter typeParameter) {
      PsiType t = mySubstitutionMap.get(typeParameter);
      if (myKind == SubstituteKind.SIMPLE) {
        return t;
      }
      else if (myKind == SubstituteKind.ADD_BOUNDS) {
        return addBounds(t, typeParameter);
      }

      return t;
    }

    private PsiType substituteInternal(PsiType type) {
      return type.accept(this);
    }

    private boolean processClass(PsiClass resolve, PsiSubstitutor originalSubstitutor, final Map<PsiTypeParameter, PsiType> substMap) {
      final PsiTypeParameter[] params = resolve.getTypeParameters();
      for (final PsiTypeParameter param : params) {
        final PsiType original = originalSubstitutor.substitute(param);
        if (original == null) {
          substMap.put(param, null);
        } else {
          final PsiType substituted = substituteInternal(original);
          if (substituted == null) return false;
          substMap.put(param, substituted);
        }
      }
      if (resolve.hasModifierProperty(PsiModifier.STATIC)) return true;

      final PsiClass containingClass = resolve.getContainingClass();
      return containingClass == null ||
             processClass(containingClass, originalSubstitutor, substMap);
    }
  }

  private PsiType addBounds(PsiType substituted, final PsiTypeParameter typeParameter) {
    PsiElement captureContext = null;
    if (substituted instanceof PsiCapturedWildcardType) {
      final PsiCapturedWildcardType captured = (PsiCapturedWildcardType)substituted;
      substituted = captured.getWildcard();
      captureContext = captured.getContext();
    }
    if (substituted instanceof PsiWildcardType && !((PsiWildcardType)substituted).isSuper()) {
      PsiType originalBound = ((PsiWildcardType)substituted).getBound();
      PsiManager manager = typeParameter.getManager();
      final PsiType[] boundTypes = typeParameter.getExtendsListTypes();
      for (PsiType boundType : boundTypes) {
        PsiType substitutedBoundType = boundType.accept(mySimpleSubstitutionVisitor);
        PsiWildcardType wildcardType = (PsiWildcardType)substituted;
        if (substitutedBoundType != null && !(substitutedBoundType instanceof PsiWildcardType) && !substitutedBoundType.equalsToText("java.lang.Object")) {
          if (originalBound == null || !TypeConversionUtil.erasure(substitutedBoundType).isAssignableFrom(originalBound)) { //erasure is essential to avoid infinite recursion
            if (wildcardType.isExtends()) {
              final PsiType glb = GenericsUtil.getGreatestLowerBound(wildcardType.getBound(), substitutedBoundType);
              if (glb != null) {
                substituted = PsiWildcardType.createExtends(manager, glb);
              }
            }
            else {
              //unbounded
              substituted = PsiWildcardType.createExtends(manager, substitutedBoundType);
            }
          }
        }
      }
    }

    if (captureContext != null) {
      LOG.assertTrue(substituted instanceof PsiWildcardType);
      substituted = PsiCapturedWildcardType.create((PsiWildcardType)substituted, captureContext);
    }
    return substituted;
  }

  private PsiType correctExternalSubstitution(PsiType substituted, final PsiType original) {
    if (original == null) return null;

    if (substituted == null) {
      return original.accept(new PsiTypeVisitor<PsiType>() {
        public PsiType visitArrayType(PsiArrayType arrayType) {
          return new PsiArrayType(arrayType.getComponentType().accept(this));
        }

        public PsiType visitEllipsisType(PsiEllipsisType ellipsisType) {
          return new PsiEllipsisType(ellipsisType.getComponentType().accept(this));
        }

        public PsiType visitClassType(PsiClassType classType) {
          PsiClass aClass = classType.resolve();
          if (aClass != null) {
            if (aClass instanceof PsiTypeParameter) {
              return rawTypeForTypeParameter((PsiTypeParameter)aClass);
            }
            else {
              return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass);
            }
          }
          else {
            return classType;
          }
        }

        public PsiType visitType(PsiType type) {
          LOG.error(type.getInternalCanonicalText());
          return null;
        }
      });
    }
    return substituted;
  }

  @Override
  protected PsiSubstitutorImpl clone() {
    return new PsiSubstitutorImpl(mySubstitutionMap);
  }

  public synchronized PsiSubstitutor put(@NotNull PsiTypeParameter typeParameter, PsiType mapping) {
    PsiSubstitutorImpl ret = clone();
    ret.mySubstitutionMap.put(typeParameter, mapping);
    return ret;
  }

  public synchronized PsiSubstitutor putAll(@NotNull PsiClass parentClass, PsiType[] mappings) {
    final PsiTypeParameter[] params = parentClass.getTypeParameters();
    PsiSubstitutorImpl substitutor = clone();
    for (int i = 0; i < params.length; i++) {
      PsiTypeParameter param = params[i];
      assert param != null;
      if (mappings != null && mappings.length > i) {
        substitutor.mySubstitutionMap.put(param, mappings[i]);
      }
      else {
        substitutor.mySubstitutionMap.put(param, null);
      }
    }

    return substitutor;
  }

  public PsiSubstitutor putAll(PsiSubstitutor another) {
    if (another instanceof EmptySubstitutorImpl) return this;
    final PsiSubstitutorImpl anotherImpl = (PsiSubstitutorImpl)another;
    PsiSubstitutorImpl substitutor = clone();
    substitutor.mySubstitutionMap.putAll(anotherImpl.mySubstitutionMap);
    return substitutor;
  }

  public String toString() {
    @NonNls StringBuilder buffer = new StringBuilder();
    final Set<Map.Entry<PsiTypeParameter, PsiType>> set = mySubstitutionMap.entrySet();
    for (Map.Entry<PsiTypeParameter, PsiType> entry : set) {
      final PsiTypeParameter typeParameter = entry.getKey();
      buffer.append(typeParameter.getName());
      final PsiElement owner = typeParameter.getOwner();
      if (owner instanceof PsiClass) {
        buffer.append(" of ");
        buffer.append(((PsiClass)owner).getQualifiedName());
      }
      else if (owner instanceof PsiMethod) {
        buffer.append(" of ");
        buffer.append(((PsiMethod)owner).getName());
        buffer.append(" in ");
        buffer.append(((PsiMethod)owner).getContainingClass().getQualifiedName());
      }
      buffer.append(" -> ");
      if (entry.getValue() != null) {
        buffer.append(entry.getValue().getCanonicalText());
      }
      else {
        buffer.append("null");
      }
      buffer.append('\n');
    }
    return buffer.toString();
  }

  public static PsiSubstitutor createSubstitutor(@Nullable Map<PsiTypeParameter, PsiType> map) {
    if (map == null || map.isEmpty()) return EMPTY;
    return new PsiSubstitutorImpl(map);
  }

  public boolean isValid() {
    Collection<PsiType> substitutorValues = mySubstitutionMap.values();
    for (PsiType type : substitutorValues) {
      if (type != null && !type.isValid()) return false;
    }
    return true;
  }

  @NotNull
  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return Collections.unmodifiableMap(mySubstitutionMap);
  }
}
