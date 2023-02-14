// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class SmartTypePointerManagerImpl extends SmartTypePointerManager {
  private static final SmartTypePointer NULL_POINTER = () -> null;

  private final SmartPointerManager myPsiPointerManager;
  private final Project myProject;

  public SmartTypePointerManagerImpl(Project project) {
    myPsiPointerManager = SmartPointerManager.getInstance(project);
    myProject = project;
  }

  @Override
  @NotNull
  public SmartTypePointer createSmartTypePointer(@NotNull PsiType type) {
    final SmartTypePointer pointer = type.accept(new SmartTypeCreatingVisitor());
    return pointer != null ? pointer : NULL_POINTER;
  }

  private static final class SimpleTypePointer implements SmartTypePointer {
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

    ArrayTypePointer(@NotNull PsiArrayType type, @NotNull SmartTypePointer componentTypePointer) {
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

    WildcardTypePointer(@NotNull PsiWildcardType type, @Nullable SmartTypePointer boundPointer) {
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
        if (type == null) return null;
        if (myIsExtending) {
          return PsiWildcardType.createExtends(myManager, type);
        }
        return PsiWildcardType.createSuper(myManager, type);
      }
    }
  }

  private static class ClassTypePointer extends TypePointerBase<PsiClassType> {
    private final SmartPsiElementPointer<?> myClass;
    private final LanguageLevel myLevel;
    private final Map<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer> myMap;
    private final SmartPsiElementPointer<?>[] myAnnotations;

    ClassTypePointer(@NotNull PsiClassType type,
                     @NotNull SmartPsiElementPointer<?> aClass,
                     @NotNull LanguageLevel languageLevel,
                     @NotNull Map<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer> map,
                     SmartPsiElementPointer<?> @NotNull [] annotations) {
      super(type);
      myClass = aClass;
      myLevel = languageLevel;
      myMap = map;
      myAnnotations = annotations;
    }

    @Override
    protected PsiClassType calcType() {
      final PsiElement classElement = myClass.getElement();
      if (!(classElement instanceof PsiClass)) return null;
      Map<PsiTypeParameter, PsiType> resurrected = new HashMap<>();
      final Set<Map.Entry<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer>> set = myMap.entrySet();
      for (Map.Entry<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer> entry : set) {
        PsiTypeParameter element = entry.getKey().getElement();
        if (element != null) {
          SmartTypePointer typePointer = entry.getValue();
          resurrected.put(element, typePointer == null ? null : typePointer.getType());
        }
      }
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable((PsiClass)classElement)) {
        if (!resurrected.containsKey(typeParameter)) {
          resurrected.put(typeParameter, null);
        }
      }
      final PsiSubstitutor resurrectedSubstitutor = PsiSubstitutor.createSubstitutor(resurrected);

      PsiAnnotation[] resurrectedAnnotations = Stream.of(myAnnotations).map(SmartPsiElementPointer::getElement).filter(Objects::nonNull).toArray(PsiAnnotation[]::new);
      return new PsiImmediateClassType((PsiClass)classElement, resurrectedSubstitutor, myLevel, resurrectedAnnotations);
    }
  }

  private final class DisjunctionTypePointer extends TypePointerBase<PsiDisjunctionType> {
    private final List<SmartTypePointer> myPointers;

    private DisjunctionTypePointer(@NotNull PsiDisjunctionType type) {
      super(type);
      myPointers = ContainerUtil.map(type.getDisjunctions(), SmartTypePointerManagerImpl.this::createSmartTypePointer);
    }

    @Override
    protected PsiDisjunctionType calcType() {
      final List<PsiType> types = ContainerUtil.map(myPointers, SmartTypePointer::getType);
      return new PsiDisjunctionType(types, PsiManager.getInstance(myProject));
    }
  }

  private class SmartTypeCreatingVisitor extends PsiTypeVisitor<SmartTypePointer> {
    @Override
    public SmartTypePointer visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
      return new SimpleTypePointer(primitiveType);
    }

    @Override
    public SmartTypePointer visitArrayType(@NotNull PsiArrayType arrayType) {
      final SmartTypePointer componentTypePointer = arrayType.getComponentType().accept(this);
      return componentTypePointer != null ? new ArrayTypePointer(arrayType, componentTypePointer) : null;
    }

    @Override
    public SmartTypePointer visitWildcardType(@NotNull PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      final SmartTypePointer boundPointer = bound == null ? null : bound.accept(this);
      return new WildcardTypePointer(wildcardType, boundPointer);
    }

    @Override
    public SmartTypePointer visitClassType(@NotNull PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) {
        return createClassReferenceTypePointer(classType);
      }
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final HashMap<SmartPsiElementPointer<PsiTypeParameter>, SmartTypePointer> pointerMap = new HashMap<>();
      final Map<PsiTypeParameter, PsiType> map = new HashMap<>();
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
        final PsiType substitutionResult = substitutor.substitute(typeParameter);
        if (substitutionResult != null) {
          final SmartPsiElementPointer<PsiTypeParameter> pointer = myPsiPointerManager.createSmartPsiElementPointer(typeParameter);
          SmartTypePointer typePointer = substitutionResult.accept(this);
          pointerMap.put(pointer, typePointer);
          map.put(typeParameter, typePointer.getType());
        } else {
          map.put(typeParameter, null);
        }
      }

      SmartPsiElementPointer<?>[] annotationPointers =
        Stream
          .of(classType.getAnnotations())
          .map(myPsiPointerManager::createSmartPsiElementPointer)
          .toArray(SmartPsiElementPointer<?>[]::new);

      LanguageLevel languageLevel = classType.getLanguageLevel();
      return new ClassTypePointer(new PsiImmediateClassType(aClass,
                                                            PsiSubstitutor.createSubstitutor(map),
                                                            languageLevel,
                                                            classType.getAnnotations()),
                                  myPsiPointerManager.createSmartPsiElementPointer(aClass),
                                  languageLevel,
                                  pointerMap,
                                  annotationPointers);
    }

    @Override
    public SmartTypePointer visitDisjunctionType(@NotNull PsiDisjunctionType disjunctionType) {
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
