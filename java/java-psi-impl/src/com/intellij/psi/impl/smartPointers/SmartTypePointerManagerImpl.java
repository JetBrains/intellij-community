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
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author max
 */
public class SmartTypePointerManagerImpl extends SmartTypePointerManager {
  private static final SmartTypePointer NULL_POINTER = new SmartTypePointer() {
    @Override
    public PsiType getType() { return null; }
  };

  private final SmartPointerManager myPsiPointerManager;
  private final Project myProject;

  public SmartTypePointerManagerImpl(final SmartPointerManager psiPointerManager, final Project project) {
    myPsiPointerManager = psiPointerManager;
    myProject = project;
  }

  @Override
  @NotNull
  public SmartTypePointer createSmartTypePointer(@NotNull PsiType type) {
    final SmartTypePointer pointer = type.accept(new SmartTypeCreatingVisitor());
    return pointer != null ? pointer : NULL_POINTER;
  }

  private static class SimpleTypePointer implements SmartTypePointer {
    private final PsiType myType;

    private SimpleTypePointer(@NotNull PsiType type) {
      myType = type;
    }

    @Override
    public PsiType getType() {
      return myType;
    }
  }

  private static class ArrayTypePointer extends TypePointerBase<PsiArrayType> {
    private final SmartTypePointer myComponentTypePointer;

    public ArrayTypePointer(@NotNull PsiArrayType type, @NotNull SmartTypePointer componentTypePointer) {
      super(type);
      myComponentTypePointer = componentTypePointer;
    }

    @Nullable
    @Override
    protected PsiArrayType calcType() {
      final PsiType type = myComponentTypePointer.getType();
      return type == null ? null : new PsiArrayType(type);
    }
  }

  private static class WildcardTypePointer extends TypePointerBase<PsiWildcardType> {
    private final PsiManager myManager;
    private final SmartTypePointer myBoundPointer;
    private final boolean myIsExtending;

    public WildcardTypePointer(@NotNull PsiWildcardType type, @Nullable SmartTypePointer boundPointer) {
      super(type);
      myManager = type.getManager();
      myBoundPointer = boundPointer;
      myIsExtending = type.isExtends();
    }

    @Override
    protected PsiWildcardType calcType() {
      if (myBoundPointer == null) {
        return PsiWildcardType.createUnbounded(myManager);
      }
      else {
        final PsiType type = myBoundPointer.getType();
        assert type != null : myBoundPointer;
        if (myIsExtending) {
          return PsiWildcardType.createExtends(myManager, type);
        }
        return PsiWildcardType.createSuper(myManager, type);
      }
    }
  }

  private static class ClassTypePointer extends TypePointerBase<PsiClassType> {
    private final SmartPsiElementPointer myClass;
    private final Map<SmartPsiElementPointer, SmartTypePointer> myMap;

    public ClassTypePointer(@NotNull PsiClassType type, @NotNull SmartPsiElementPointer aClass, @NotNull Map<SmartPsiElementPointer, SmartTypePointer> map) {
      super(type);
      myClass = aClass;
      myMap = map;
    }

    @Override
    protected PsiClassType calcType() {
      final PsiElement classElement = myClass.getElement();
      if (!(classElement instanceof PsiClass)) return null;
      Map<PsiTypeParameter, PsiType> resurrected = new HashMap<PsiTypeParameter, PsiType>();
      final Set<Map.Entry<SmartPsiElementPointer, SmartTypePointer>> set = myMap.entrySet();
      for (Map.Entry<SmartPsiElementPointer, SmartTypePointer> entry : set) {
        PsiElement element = entry.getKey().getElement();
        if (element instanceof PsiTypeParameter) {
          SmartTypePointer typePointer = entry.getValue();
          resurrected.put((PsiTypeParameter)element, typePointer == null ? null : typePointer.getType());
        }
      }
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable((PsiClass)classElement)) {
        if (!resurrected.containsKey(typeParameter)) {
          resurrected.put(typeParameter, null);
        }
      }
      final PsiSubstitutor resurrectedSubstitutor = PsiSubstitutorImpl.createSubstitutor(resurrected);
      return new PsiImmediateClassType((PsiClass)classElement, resurrectedSubstitutor);
    }
  }

  private class DisjunctionTypePointer extends TypePointerBase<PsiDisjunctionType> {
    private final List<SmartTypePointer> myPointers;

    private DisjunctionTypePointer(@NotNull PsiDisjunctionType type) {
      super(type);
      myPointers = ContainerUtil.map(type.getDisjunctions(), new Function<PsiType, SmartTypePointer>() {
        @Override
        public SmartTypePointer fun(PsiType psiType) {
          return createSmartTypePointer(psiType);
        }
      });
    }

    @Override
    protected PsiDisjunctionType calcType() {
      final List<PsiType> types = ContainerUtil.map(myPointers, new NullableFunction<SmartTypePointer, PsiType>() {
        @Override public PsiType fun(SmartTypePointer typePointer) { return typePointer.getType(); }
      });
      return new PsiDisjunctionType(types, PsiManager.getInstance(myProject));
    }
  }

  private class SmartTypeCreatingVisitor extends PsiTypeVisitor<SmartTypePointer> {
    @Override
    public SmartTypePointer visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return new SimpleTypePointer(primitiveType);
    }

    @Override
    public SmartTypePointer visitArrayType(PsiArrayType arrayType) {
      final SmartTypePointer componentTypePointer = arrayType.getComponentType().accept(this);
      return componentTypePointer != null ? new ArrayTypePointer(arrayType, componentTypePointer) : null;
    }

    @Override
    public SmartTypePointer visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      final SmartTypePointer boundPointer = bound == null ? null : bound.accept(this);
      return new WildcardTypePointer(wildcardType, boundPointer);
    }

    @Override
    public SmartTypePointer visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) {
        return createClassReferenceTypePointer(classType);
      }
      if (classType instanceof PsiClassReferenceType) {
        classType = ((PsiClassReferenceType)classType).createImmediateCopy();
      }
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final HashMap<SmartPsiElementPointer, SmartTypePointer> map = new HashMap<SmartPsiElementPointer, SmartTypePointer>();
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
        final PsiType substitutionResult = substitutor.substitute(typeParameter);
        if (substitutionResult != null) {
          final SmartPsiElementPointer pointer = myPsiPointerManager.createSmartPsiElementPointer(typeParameter);
          map.put(pointer, substitutionResult.accept(this));
        }
      }
      return new ClassTypePointer(classType, myPsiPointerManager.createSmartPsiElementPointer(aClass), map);
    }

    @Override
    public SmartTypePointer visitDisjunctionType(PsiDisjunctionType disjunctionType) {
      return new DisjunctionTypePointer(disjunctionType);
    }
  }

  @NotNull
  private SmartTypePointer createClassReferenceTypePointer(@NotNull PsiClassType classType) {
    for (ClassTypePointerFactory factory : ClassTypePointerFactory.EP_NAME.getExtensions()) {
      SmartTypePointer pointer = factory.createClassTypePointer(classType, myProject);
      if (pointer != null) {
        return pointer;
      }
    }

    return new SimpleTypePointer(classType);
  }

}
