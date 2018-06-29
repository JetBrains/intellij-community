/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameter;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.UnmodifiableTHashMap;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author ik, dsl
 */
public class PsiSubstitutorImpl implements PsiSubstitutor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiSubstitutorImpl");

  private static final TObjectHashingStrategy<PsiTypeParameter> PSI_EQUIVALENCE = new TObjectHashingStrategy<PsiTypeParameter>() {
    @Override
    public int computeHashCode(PsiTypeParameter parameter) {
      return Comparing.hashcode(parameter.getName());
    }

    @Override
    public boolean equals(PsiTypeParameter element1, PsiTypeParameter element2) {
      return element1.getManager().areElementsEquivalent(element1, element2);
    }
  };

  private final Map<PsiTypeParameter, PsiType> mySubstitutionMap;
  private final SubstitutionVisitor mySimpleSubstitutionVisitor = new SubstitutionVisitor();

  private PsiSubstitutorImpl(@NotNull Map<PsiTypeParameter, PsiType> map) {
    mySubstitutionMap = new UnmodifiableTHashMap<>(PSI_EQUIVALENCE, map);
  }

  private PsiSubstitutorImpl(@NotNull Map<PsiTypeParameter, PsiType> map, @NotNull PsiTypeParameter additionalKey, @Nullable PsiType additionalValue) {
    mySubstitutionMap = new UnmodifiableTHashMap<>(PSI_EQUIVALENCE, map, additionalKey, additionalValue);
  }

  PsiSubstitutorImpl(@NotNull PsiTypeParameter typeParameter, PsiType mapping) {
    mySubstitutionMap = new UnmodifiableTHashMap<>(PSI_EQUIVALENCE, typeParameter, mapping);
  }

  PsiSubstitutorImpl(@NotNull PsiClass parentClass, PsiType[] mappings) {
    this(putAllInternal(Collections.emptyMap(), parentClass, mappings));
  }

  @Override
  public PsiType substitute(@NotNull PsiTypeParameter typeParameter) {
    return containsInMap(typeParameter)
                     ? getFromMap(typeParameter)
                     : JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter);
  }

  private boolean containsInMap(@NotNull PsiTypeParameter typeParameter) {
    if (typeParameter instanceof LightTypeParameter && ((LightTypeParameter)typeParameter).useDelegateToSubstitute()) {
      typeParameter = ((LightTypeParameter)typeParameter).getDelegate();
    }
    return mySubstitutionMap.containsKey(typeParameter);
  }

  private PsiType getFromMap(@NotNull PsiTypeParameter typeParameter) {
    if (typeParameter instanceof LightTypeParameter && ((LightTypeParameter)typeParameter).useDelegateToSubstitute()) {
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
    PsiType substituted = type.accept(mySimpleSubstitutionVisitor);
    return correctExternalSubstitution(substituted, type);
  }

  @Override
  public PsiType substituteWithBoundsPromotion(@NotNull PsiTypeParameter typeParameter) {
    final PsiType substituted = substitute(typeParameter);
    if (substituted instanceof PsiWildcardType && !((PsiWildcardType)substituted).isSuper()) {
      final PsiWildcardType wildcardType = (PsiWildcardType)substituted;
      final PsiType glb = PsiCapturedWildcardType.captureUpperBound(typeParameter, wildcardType, this);
      if (glb instanceof PsiWildcardType) {
        return glb;
      }
      if (glb instanceof PsiCapturedWildcardType) {
        PsiWildcardType wildcard = ((PsiCapturedWildcardType)glb).getWildcard();
        if (!wildcard.isSuper()) return wildcard;
      }

      if (glb != null) {
        return PsiWildcardType.createExtends(typeParameter.getManager(), glb);
      }
    }
    return substituted;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof PsiSubstitutorImpl)) return false;

    final PsiSubstitutorImpl that = (PsiSubstitutorImpl)o;

    if (mySubstitutionMap != null ? !mySubstitutionMap.equals(that.mySubstitutionMap) : that.mySubstitutionMap != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return mySubstitutionMap != null ? mySubstitutionMap.hashCode() : 0;
  }

  private static final RecursionGuard ourGuard = RecursionManager.createGuard("substituteGuard");
  private PsiType rawTypeForTypeParameter(@NotNull PsiTypeParameter typeParameter) {
    final PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
    if (extendsTypes.length > 0) {
      // First bound
      return ourGuard.doPreventingRecursion(extendsTypes[0], true, () -> substitute(extendsTypes[0]));
    }
    // Object
    return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
  }

  @NotNull
  private static TypeAnnotationProvider getMergedProvider(@NotNull PsiType type1, @NotNull PsiType type2) {
    if(type1.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type1 instanceof PsiClassReferenceType)) {
      return type2.getAnnotationProvider();
    }
    if(type2.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type2 instanceof PsiClassReferenceType)) {
      return type1.getAnnotationProvider();
    }
    return () -> ArrayUtil.mergeArrays(type1.getAnnotations(), type2.getAnnotations());
  }

  private class SubstitutionVisitor extends PsiTypeMapper {

    @Override
    public PsiType visitType(PsiType type) {
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
          final PsiType newBoundBound = ((PsiWildcardType)newBound).getBound();
          return !((PsiWildcardType)newBound).isBounded() ? PsiWildcardType.createUnbounded(wildcardType.getManager())
                                                          : rebound(wildcardType, newBoundBound);
        }

        return newBound == PsiType.NULL ? newBound : rebound(wildcardType, newBound);
      }
    }

    @NotNull
    private PsiWildcardType rebound(@NotNull PsiWildcardType type, @NotNull PsiType newBound) {
      LOG.assertTrue(type.getBound() != null);
      LOG.assertTrue(newBound.isValid());

      if (type.isExtends()) {
        if (newBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          return PsiWildcardType.createUnbounded(type.getManager());
        }
        return PsiWildcardType.createExtends(type.getManager(), newBound);
      }
      return PsiWildcardType.createSuper(type.getManager(), newBound);
    }

    @Override
    public PsiType visitClassType(final PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return classType;

      PsiUtilCore.ensureValid(aClass);
      if (aClass instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter = (PsiTypeParameter)aClass;
        if (containsInMap(typeParameter)) {
          final PsiType result = substituteTypeParameter(typeParameter);
          if (result != null) {
            PsiUtil.ensureValidType(result);
            if (result instanceof PsiClassType || result instanceof PsiArrayType || result instanceof PsiWildcardType) {
              return result.annotate(getMergedProvider(classType, result));
            }
          }
          return result;
        }
        return classType;
      }
      final Map<PsiTypeParameter, PsiType> hashMap = new HashMap<>(2);
      if (!processClass(aClass, resolveResult.getSubstitutor(), hashMap)) {
        return null;
      }
      PsiClassType result = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass, createSubstitutor(hashMap), classType.getLanguageLevel());
      PsiUtil.ensureValidType(result);
      return result.annotate(classType.getAnnotationProvider());
    }

    private PsiType substituteTypeParameter(@NotNull PsiTypeParameter typeParameter) {
      return getFromMap(typeParameter);
    }

    private PsiType substituteInternal(@NotNull PsiType type) {
      return type.accept(this);
    }

    private boolean processClass(@NotNull PsiClass resolve, @NotNull PsiSubstitutor originalSubstitutor, @NotNull Map<PsiTypeParameter, PsiType> substMap) {
      final PsiTypeParameter[] params = resolve.getTypeParameters();
      for (final PsiTypeParameter param : params) {
        final PsiType original = originalSubstitutor.substitute(param);
        if (original == null) {
          substMap.put(param, null);
        }
        else {
          substMap.put(param, substituteInternal(original));
        }
      }
      if (resolve.hasModifierProperty(PsiModifier.STATIC)) return true;

      final PsiClass containingClass = resolve.getContainingClass();
      return containingClass == null ||
             processClass(containingClass, originalSubstitutor, substMap);
    }
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
        return null;
      }
    });
  }

  @Override
  protected PsiSubstitutorImpl clone() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiSubstitutor put(@NotNull PsiTypeParameter typeParameter, PsiType mapping) {
    if (mapping != null && !mapping.isValid()) {
      LOG.error("Invalid type in substitutor: " + mapping + "; " + mapping.getClass());
    }
    return new PsiSubstitutorImpl(mySubstitutionMap, typeParameter, mapping);
  }

  private static Map<PsiTypeParameter, PsiType> putAllInternal(Map<PsiTypeParameter, PsiType> originalMap, @NotNull PsiClass parentClass,
                                                               PsiType[] mappings) {
    final PsiTypeParameter[] params = parentClass.getTypeParameters();
    if (params.length == 0) return originalMap;
    Map<PsiTypeParameter, PsiType> newMap = new THashMap<>(originalMap);

    for (int i = 0; i < params.length; i++) {
      PsiTypeParameter param = params[i];
      assert param != null;
      if (mappings != null && mappings.length > i) {
        PsiType mapping = mappings[i];
        newMap.put(param, mapping);
        if (mapping != null && !mapping.isValid()) {
          LOG.error("Invalid type in substitutor: " + mapping);
        }
      }
      else {
        newMap.put(param, null);
      }
    }
    return newMap;
  }

  @NotNull
  @Override
  public PsiSubstitutor putAll(@NotNull PsiClass parentClass, PsiType[] mappings) {
    return new PsiSubstitutorImpl(putAllInternal(mySubstitutionMap, parentClass, mappings));
  }

  @NotNull
  @Override
  public PsiSubstitutor putAll(@NotNull PsiSubstitutor another) {
    if (another instanceof EmptySubstitutorImpl) {
      return this;
    }
    final PsiSubstitutorImpl anotherImpl = (PsiSubstitutorImpl)another;
    Map<PsiTypeParameter, PsiType> newMap = new THashMap<>(mySubstitutionMap, PSI_EQUIVALENCE);
    newMap.putAll(anotherImpl.mySubstitutionMap);
    return createSubstitutor(newMap);
  }

  @Override
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
        PsiClass aClass = ((PsiMethod)owner).getContainingClass();
        buffer.append(aClass != null ? aClass.getQualifiedName() : "<no class>");
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

  @NotNull
  public static PsiSubstitutor createSubstitutor(@Nullable Map<PsiTypeParameter, PsiType> map) {
    if (map == null || map.isEmpty()) return EMPTY;
    return new PsiSubstitutorImpl(map);
  }

  @Override
  public boolean isValid() {
    for (PsiType type : mySubstitutionMap.values()) {
      if (type != null && !type.isValid()) return false;
    }
    return true;
  }

  @Override
  public void ensureValid() {
    for (PsiType type : mySubstitutionMap.values()) {
      if (type != null) {
        PsiUtil.ensureValidType(type);
      }
    }
  }

  @Override
  @NotNull
  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return mySubstitutionMap;
  }
}
