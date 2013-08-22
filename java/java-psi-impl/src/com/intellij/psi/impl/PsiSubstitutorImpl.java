/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.impl.light.LightTypeParameter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ik, dsl
 */
public class PsiSubstitutorImpl implements PsiSubstitutor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiSubstitutorImpl");

  private static final TObjectHashingStrategy<PsiTypeParameter> PSI_EQUIVALENCE = new TObjectHashingStrategy<PsiTypeParameter>() {
    @Override
    public int computeHashCode(PsiTypeParameter object) {
      String name = object.getName();
      return name == null ? 0 : name.hashCode();
    }

    @Override
    public boolean equals(PsiTypeParameter element1, PsiTypeParameter element2) {
      return element1.getManager().areElementsEquivalent(element1, element2);
    }
  };

  private final Map<PsiTypeParameter, PsiType> mySubstitutionMap;

  private PsiSubstitutorImpl(@NotNull Map<PsiTypeParameter, PsiType> map) {
    mySubstitutionMap = new THashMap<PsiTypeParameter, PsiType>(map, PSI_EQUIVALENCE);
  }

  PsiSubstitutorImpl() {
    mySubstitutionMap = new THashMap<PsiTypeParameter, PsiType>(2, PSI_EQUIVALENCE);
  }

  PsiSubstitutorImpl(@NotNull PsiTypeParameter typeParameter, PsiType mapping) {
    this();
    mySubstitutionMap.put(typeParameter, mapping);
  }

  PsiSubstitutorImpl(@NotNull PsiClass parentClass, PsiType[] mappings) {
    this();
    putAllInternal(parentClass, mappings);
  }

  @Override
  public PsiType substitute(@NotNull PsiTypeParameter typeParameter) {
    if (containsInMap(typeParameter)) {
      return getFromMap(typeParameter);
    }
    return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter);
  }

  private boolean containsInMap(PsiTypeParameter typeParameter) {
    if (typeParameter instanceof LightTypeParameter) {
      typeParameter = ((LightTypeParameter)typeParameter).getDelegate();
    }
    return mySubstitutionMap.containsKey(typeParameter);
  }

  private PsiType getFromMap(PsiTypeParameter typeParameter) {
    if (typeParameter instanceof LightTypeParameter) {
      typeParameter = ((LightTypeParameter)typeParameter).getDelegate();
    }
    return mySubstitutionMap.get(typeParameter);
  }

  @Override
  public PsiType substitute(PsiType type) {
    if (type == null) {
      //noinspection ConstantConditions
      return null;
    }
    PsiUtil.ensureValidType(type);
    PsiType substituted = type.accept(myAddingBoundsSubstitutionVisitor);
    return correctExternalSubstitution(substituted, type);
  }

  @Override
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
    // Object
    return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
  }

  private abstract static class SubstitutionVisitorBase extends PsiTypeVisitorEx<PsiType> {
    @Override
    public PsiType visitType(PsiType type) {
      LOG.assertTrue(false);
      return null;
    }

    @Override
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
        assert newBound.isValid() : newBound.getClass() + "; " + bound.isValid();
        if (newBound instanceof PsiWildcardType) {
          return handleBoundComposition(wildcardType, (PsiWildcardType)newBound);
        }
        if (newBound instanceof PsiCapturedWildcardType) {
          final PsiWildcardType wildcard = ((PsiCapturedWildcardType)newBound).getWildcard();
          if (wildcardType.isExtends() != wildcard.isExtends()) {
            if (wildcard.isBounded()) {
              return wildcardType.isExtends() ? PsiWildcardType.createExtends(wildcardType.getManager(), newBound)
                                              : PsiWildcardType.createSuper(wildcardType.getManager(), newBound);
            }
            else {
              return newBound;
            }
          }
          if (!wildcard.isBounded()) return PsiWildcardType.createUnbounded(wildcardType.getManager());
          return newBound;
        }

        return rebound(wildcardType, newBound);
      }
    }

    private static PsiType handleBoundComposition(PsiWildcardType wildcardType, PsiWildcardType bound) {
      if (bound.isExtends() == wildcardType.isExtends()) {
        final PsiType newBoundBound = bound.getBound();
        if (newBoundBound != null) {
          return rebound(wildcardType, newBoundBound);
        }
      }
      return PsiWildcardType.createUnbounded(wildcardType.getManager());
    }

    private static PsiWildcardType rebound(PsiWildcardType type, PsiType newBound) {
      LOG.assertTrue(type.getBound() != null);
      LOG.assertTrue(newBound.isValid());

      if (type.isExtends()) {
        if (newBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          return PsiWildcardType.createUnbounded(type.getManager());
        }
        else {
          return PsiWildcardType.createExtends(type.getManager(), newBound);
        }
      }
      else {
        return PsiWildcardType.createSuper(type.getManager(), newBound);
      }
    }

    @Override
    public PsiType visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return primitiveType;
    }

    @Override
    public PsiType visitArrayType(PsiArrayType arrayType) {
      final PsiType componentType = arrayType.getComponentType();
      final PsiType substitutedComponentType = componentType.accept(this);
      if (substitutedComponentType == null) return null;
      if (substitutedComponentType == componentType) return arrayType; // optimization
      return new PsiArrayType(substitutedComponentType);
    }

    @Override
    public PsiType visitEllipsisType(PsiEllipsisType ellipsisType) {
      final PsiType componentType = ellipsisType.getComponentType();
      final PsiType substitutedComponentType = componentType.accept(this);
      if (substitutedComponentType == null) return null;
      if (substitutedComponentType == componentType) return ellipsisType; // optimization
      return new PsiEllipsisType(substitutedComponentType);
    }

    @Override
    public PsiType visitTypeVariable(final PsiTypeVariable var) {
      return var;
    }

    @Override
    public PsiType visitBottom(final Bottom bottom) {
      return bottom;
    }

    @Override
    public abstract PsiType visitClassType(PsiClassType classType);

    @Override
    public PsiType visitDisjunctionType(PsiDisjunctionType disjunctionType) {
      final List<PsiType> substituted = ContainerUtil.map(disjunctionType.getDisjunctions(), new Function<PsiType, PsiType>() {
        @Override public PsiType fun(PsiType psiType) { return psiType.accept(SubstitutionVisitorBase.this); }
      });
      return disjunctionType.newDisjunctionType(substituted);
    }

    @Override
    public PsiType visitDiamondType(PsiDiamondType diamondType) {
      return diamondType;
    }
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

    @Override
    public PsiType visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return classType;

      PsiUtilCore.ensureValid(aClass);
      if (aClass instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter = (PsiTypeParameter)aClass;
        if (containsInMap(typeParameter)) {
          PsiType result = substituteTypeParameter(typeParameter);
          if (result != null) {
            PsiUtil.ensureValidType(result);
          }
          return result;
        }
        return classType;
      }
      final Map<PsiTypeParameter, PsiType> hashMap = new HashMap<PsiTypeParameter, PsiType>(2);
      if (!processClass(aClass, resolveResult.getSubstitutor(), hashMap)) {
        return null;
      }
      PsiClassType result = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass, createSubstitutor(hashMap), classType.getLanguageLevel());
      PsiUtil.ensureValidType(result);
      return result;
    }

    private PsiType substituteTypeParameter(final PsiTypeParameter typeParameter) {
      PsiType t = getFromMap(typeParameter);
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
          /*boolean alreadyFound = false;
          for (Map.Entry<PsiTypeParameter, PsiType> entry : substMap.entrySet()) {
            if (original.equals(originalSubstitutor.substitute(entry.getKey()))) {
              substMap.put(param, entry.getValue());
              alreadyFound = true;
            }
          }
          if (alreadyFound) continue;*/
          final PsiType substituted = substituteInternal(original);
          //if (substituted == null) return false;
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
    PsiType oldSubstituted = substituted;
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
        if (substitutedBoundType != null && !(substitutedBoundType instanceof PsiWildcardType) && !substitutedBoundType.equalsToText(
          CommonClassNames.JAVA_LANG_OBJECT)) {
          if (originalBound == null ||
              !TypeConversionUtil.erasure(substitutedBoundType).isAssignableFrom(TypeConversionUtil.erasure(originalBound)) &&
              !TypeConversionUtil.erasure(substitutedBoundType).isAssignableFrom(originalBound)) { //erasure is essential to avoid infinite recursion
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
      substituted = oldSubstituted instanceof PsiCapturedWildcardType && substituted == ((PsiCapturedWildcardType)oldSubstituted).getWildcard()
                    ? oldSubstituted : PsiCapturedWildcardType.create((PsiWildcardType)substituted, captureContext);
    }
    return substituted;
  }

  private PsiType correctExternalSubstitution(PsiType substituted, @NotNull PsiType original) {
    if (substituted != null) {
      return substituted;
    }
    return original.accept(new PsiTypeVisitor<PsiType>() {
      @Override
      public PsiType visitArrayType(PsiArrayType arrayType) {
        return new PsiArrayType(arrayType.getComponentType().accept(this));
      }

      @Override
      public PsiType visitEllipsisType(PsiEllipsisType ellipsisType) {
        return new PsiEllipsisType(ellipsisType.getComponentType().accept(this));
      }

      @Override
      public PsiType visitClassType(PsiClassType classType) {
        PsiClass aClass = classType.resolve();
        if (aClass == null) {
          return classType;
        }
        if (aClass instanceof PsiTypeParameter) {
          return rawTypeForTypeParameter((PsiTypeParameter)aClass);
        }
        return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass);
      }

      @Override
      public PsiType visitType(PsiType type) {
        LOG.error(type.getInternalCanonicalText());
        return null;
      }
    });
  }

  @Override
  protected PsiSubstitutorImpl clone() {
    return new PsiSubstitutorImpl(mySubstitutionMap);
  }

  @Override
  public PsiSubstitutor put(@NotNull PsiTypeParameter typeParameter, PsiType mapping) {
    PsiSubstitutorImpl ret = clone();
    if (mapping != null && !mapping.isValid()) {
      LOG.error("Invalid type in substitutor: " + mapping + "; " + mapping.getClass());
    }
    ret.mySubstitutionMap.put(typeParameter, mapping);
    return ret;
  }

  private void putAllInternal(@NotNull PsiClass parentClass, PsiType[] mappings) {
    final PsiTypeParameter[] params = parentClass.getTypeParameters();

    for (int i = 0; i < params.length; i++) {
      PsiTypeParameter param = params[i];
      assert param != null;
      if (mappings != null && mappings.length > i) {
        PsiType mapping = mappings[i];
        mySubstitutionMap.put(param, mapping);
        if (mapping != null && !mapping.isValid()) {
          LOG.error("Invalid type in substitutor: " + mapping);
        }
      }
      else {
        mySubstitutionMap.put(param, null);
      }
    }
  }

  @Override
  public PsiSubstitutor putAll(@NotNull PsiClass parentClass, PsiType[] mappings) {
    PsiSubstitutorImpl substitutor = clone();
    substitutor.putAllInternal(parentClass, mappings);
    return substitutor;
  }

  @Override
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

  @Override
  public boolean isValid() {
    Collection<PsiType> substitutorValues = mySubstitutionMap.values();
    for (PsiType type : substitutorValues) {
      if (type != null && !type.isValid()) return false;
    }
    return true;
  }

  @Override
  @NotNull
  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return Collections.unmodifiableMap(mySubstitutionMap);
  }
}
