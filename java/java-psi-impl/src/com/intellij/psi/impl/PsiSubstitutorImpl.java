// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameter;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.UnmodifiableHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class PsiSubstitutorImpl implements PsiSubstitutor {
  private static final Logger LOG = Logger.getInstance(PsiSubstitutorImpl.class);

  private static final HashingStrategy<PsiTypeParameter> PSI_EQUIVALENCE = new HashingStrategy<PsiTypeParameter>() {
    @Override
    public int hashCode(PsiTypeParameter parameter) {
      return Comparing.hashcode(parameter.getName());
    }

    @Override
    public boolean equals(PsiTypeParameter element1, PsiTypeParameter element2) {
      return element1.getManager().areElementsEquivalent(element1, element2);
    }
  };
  private static final UnmodifiableHashMap<PsiTypeParameter, PsiType> EMPTY_MAP = UnmodifiableHashMap.empty(PSI_EQUIVALENCE);

  @NotNull
  private final UnmodifiableHashMap<PsiTypeParameter, PsiType> mySubstitutionMap;
  private final SubstitutionVisitor mySimpleSubstitutionVisitor = new SubstitutionVisitor();

  PsiSubstitutorImpl(@NotNull Map<? extends PsiTypeParameter, ? extends PsiType> map) {
    mySubstitutionMap = UnmodifiableHashMap.fromMap(PSI_EQUIVALENCE, map);
  }

  private PsiSubstitutorImpl(@NotNull UnmodifiableHashMap<PsiTypeParameter, PsiType> map,
                             @NotNull PsiTypeParameter additionalKey,
                             @Nullable PsiType additionalValue) {
    mySubstitutionMap = map.with(additionalKey, additionalValue);
  }

  PsiSubstitutorImpl(@NotNull PsiTypeParameter typeParameter, PsiType mapping) {
    mySubstitutionMap = EMPTY_MAP.with(typeParameter, mapping);
  }

  PsiSubstitutorImpl(@NotNull PsiClass parentClass, PsiType[] mappings) {
    this(putAllInternal(EMPTY_MAP, parentClass, mappings));
  }

  @Override
  public PsiType substitute(@NotNull PsiTypeParameter typeParameter) {
    PsiType type = getFromMap(typeParameter);
    return PsiTypes.voidType().equals(type) ? JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(typeParameter) : type;
  }

  /**
   * @return type mapped to type parameter; null if type parameter is mapped to null; or PsiType.VOID if no mapping exists
   */
  private PsiType getFromMap(@NotNull PsiTypeParameter typeParameter) {
    if (typeParameter instanceof LightTypeParameter && ((LightTypeParameter)typeParameter).useDelegateToSubstitute()) {
      typeParameter = ((LightTypeParameter)typeParameter).getDelegate();
    }
    return mySubstitutionMap.getOrDefault(typeParameter, PsiTypes.voidType());
  }

  @Override
  public PsiType substitute(PsiType type) {
    if (type == null) {
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
    return o instanceof PsiSubstitutorImpl && mySubstitutionMap.equals(((PsiSubstitutorImpl)o).mySubstitutionMap);
  }

  @Override
  public int hashCode() {
    return mySubstitutionMap.hashCode();
  }

  private PsiType rawTypeForTypeParameter(@NotNull PsiTypeParameter typeParameter) {
    final PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
    if (extendsTypes.length > 0) {
      // First bound
      return RecursionManager.doPreventingRecursion(extendsTypes[0], true, () -> substitute(extendsTypes[0]));
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
    public PsiType visitType(@NotNull PsiType type) {
      return null;
    }

    @Override
    public PsiType visitWildcardType(@NotNull PsiWildcardType wildcardType) {
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

        return newBound == PsiTypes.nullType() ? newBound : rebound(wildcardType, newBound);
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
    public PsiType visitClassType(@NotNull final PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return classType;

      PsiUtilCore.ensureValid(aClass);
      if (aClass instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter = (PsiTypeParameter)aClass;
        final PsiType result = getFromMap(typeParameter);
        if (PsiTypes.voidType().equals(result)) {
          return classType;
        }
        if (result != null) {
          PsiUtil.ensureValidType(result);
          if (result instanceof PsiClassType || result instanceof PsiArrayType || result instanceof PsiWildcardType) {
            return result.annotate(getMergedProvider(classType, result));
          }
        }
        return result;
      }
      final Map<PsiTypeParameter, PsiType> hashMap = new HashMap<>(2);
      if (!processClass(aClass, resolveResult.getSubstitutor(), hashMap)) {
        return null;
      }
      PsiClassType result = JavaPsiFacade.getElementFactory(aClass.getProject())
        .createType(aClass, PsiSubstitutor.createSubstitutor(hashMap), classType.getLanguageLevel());
      PsiUtil.ensureValidType(result);
      return result.annotate(classType.getAnnotationProvider());
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
      public PsiType visitArrayType(@NotNull PsiArrayType arrayType) {
        return new PsiArrayType(arrayType.getComponentType().accept(this));
      }

      @Override
      public PsiType visitEllipsisType(@NotNull PsiEllipsisType ellipsisType) {
        return new PsiEllipsisType(ellipsisType.getComponentType().accept(this));
      }

      @Override
      public PsiType visitClassType(@NotNull PsiClassType classType) {
        PsiClass aClass = classType.resolve();
        if (aClass == null) {
          return classType;
        }
        if (aClass instanceof PsiTypeParameter) {
          return rawTypeForTypeParameter((PsiTypeParameter)aClass);
        }
        return JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);
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

  @NotNull
  private static UnmodifiableHashMap<PsiTypeParameter, PsiType> putAllInternal(@NotNull UnmodifiableHashMap<PsiTypeParameter, PsiType> originalMap,
                                                                               @NotNull PsiClass parentClass,
                                                                               PsiType[] mappings) {
    final PsiTypeParameter[] params = parentClass.getTypeParameters();
    if (params.length == 0) return originalMap;
    UnmodifiableHashMap<PsiTypeParameter, PsiType> newMap = originalMap;

    for (int i = 0; i < params.length; i++) {
      PsiTypeParameter param = params[i];
      assert param != null;
      if (mappings != null && mappings.length > i) {
        PsiType mapping = mappings[i];
        newMap = newMap.with(param, mapping);
        if (mapping != null && !mapping.isValid()) {
          LOG.error("Invalid type in substitutor: " + mapping);
        }
      }
      else {
        newMap = newMap.with(param, null);
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
    if (another instanceof EmptySubstitutor) {
      return this;
    }
    final PsiSubstitutorImpl anotherImpl = (PsiSubstitutorImpl)another;
    return putAll(anotherImpl.mySubstitutionMap);
  }

  @NotNull
  @Override
  public PsiSubstitutor putAll(@NotNull Map<? extends PsiTypeParameter, ? extends PsiType> map) {
    if (map.isEmpty()) return this;
    return new PsiSubstitutorImpl(mySubstitutionMap.withAll(map));
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

  @Override
  public boolean isValid() {
    for (Map.Entry<PsiTypeParameter, PsiType> entry : mySubstitutionMap.entrySet()) {
      if (!entry.getKey().isValid()) return false;

      PsiType type = entry.getValue();
      if (type != null && !type.isValid()) return false;
    }
    return true;
  }

  @Override
  public void ensureValid() {
    mySubstitutionMap.values().forEach(type -> {
      if (type != null) {
        PsiUtil.ensureValidType(type);
      }
    });
  }

  @Override
  @NotNull
  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return mySubstitutionMap;
  }
}
