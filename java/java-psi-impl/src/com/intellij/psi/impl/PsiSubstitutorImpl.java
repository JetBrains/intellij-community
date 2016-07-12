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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
  private final SubstitutionVisitor mySimpleSubstitutionVisitor = new SubstitutionVisitor();

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

  private static RecursionGuard ourGuard = RecursionManager.createGuard("substituteGuard");
  private PsiType rawTypeForTypeParameter(final PsiTypeParameter typeParameter) {
    final PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
    if (extendsTypes.length > 0) {
      // First bound
      return ourGuard.doPreventingRecursion(extendsTypes[0], true, new Computable<PsiType>() {
        @Override
        public PsiType compute() {
          return substitute(extendsTypes[0]);
        }
      });
    }
    // Object
    return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
  }

  private class SubstitutionVisitor extends PsiTypeMapper {
    @Override
    public PsiType visitCapturedWildcardType(PsiCapturedWildcardType type) {
      return type;
    }

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

    private PsiWildcardType rebound(PsiWildcardType type, PsiType newBound) {
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
              return result.annotate(new TypeAnnotationProvider() {
                @NotNull
                @Override
                public PsiAnnotation[] getAnnotations() {
                  return ArrayUtil.mergeArrays(result.getAnnotations(), classType.getAnnotations());
                }
              });
            }
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

    private PsiType substituteTypeParameter(@NotNull PsiTypeParameter typeParameter) {
      return getFromMap(typeParameter);
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
    return new PsiSubstitutorImpl(mySubstitutionMap);
  }

  @NotNull
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

  @NotNull
  @Override
  public PsiSubstitutor putAll(@NotNull PsiClass parentClass, PsiType[] mappings) {
    PsiSubstitutorImpl substitutor = clone();
    substitutor.putAllInternal(parentClass, mappings);
    return substitutor;
  }

  @NotNull
  @Override
  public PsiSubstitutor putAll(@NotNull PsiSubstitutor another) {
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
    return Collections.unmodifiableMap(mySubstitutionMap);
  }
}
