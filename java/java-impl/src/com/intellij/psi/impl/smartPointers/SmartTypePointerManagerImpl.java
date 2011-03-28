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
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartTypePointerManagerImpl");

  private final SmartPointerManager myPsiPointerManager;
  private final Project myProject;

  public SmartTypePointerManagerImpl(final SmartPointerManager psiPointerManager, final Project project) {
    myPsiPointerManager = psiPointerManager;
    myProject = project;
  }

  @NotNull
  public SmartTypePointer createSmartTypePointer(@NotNull PsiType type) {
    return type.accept(new SmartTypeCreatingVisitor());
  }

  private static class SimpleTypePointer implements SmartTypePointer {
    private final PsiType myType;

    private SimpleTypePointer(PsiType type) {
      myType = type;
    }

    public PsiType getType() {
      return myType;
    }
  }

  private static class ArrayTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final SmartTypePointer myComponentTypePointer;

    public ArrayTypePointer(PsiType type, SmartTypePointer componentTypePointer) {
      myType = type;
      myComponentTypePointer = componentTypePointer;
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      final PsiType type = myComponentTypePointer.getType();
      if (type == null) {
        myType = null;
      }
      else {
        myType = new PsiArrayType(type);
      }
      return myType;
    }
  }

  private static class WildcardTypePointer implements SmartTypePointer {
    private final PsiWildcardType myType;
    private final PsiManager myManager;
    private final SmartTypePointer myBoundPointer;
    private final boolean myIsExtending;

    public WildcardTypePointer(PsiWildcardType type, SmartTypePointer boundPointer) {
      myType = type;
      myManager = myType.getManager();
      myBoundPointer = boundPointer;
      myIsExtending = myType.isExtends();
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      if (myBoundPointer == null) {
        return PsiWildcardType.createUnbounded(myManager);
      }
      else {
        final PsiType type = myBoundPointer.getType();
        assert type != null : myBoundPointer;
        if (myIsExtending) {
          return PsiWildcardType.createExtends(myManager, type);
        }
        else {
          return PsiWildcardType.createSuper(myManager, type);
        }
      }
    }
  }

  private static class ClassTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final SmartPsiElementPointer myClass;
    private final Map<SmartPsiElementPointer, SmartTypePointer> myMap;

    public ClassTypePointer(PsiType type, SmartPsiElementPointer aClass, Map<SmartPsiElementPointer, SmartTypePointer> map) {
      myType = type;
      myClass = aClass;
      myMap = map;
    }

    @Nullable
    public PsiType getType() {
      if (myType.isValid()) return myType;
      final PsiElement classElement = myClass.getElement();
      if (!(classElement instanceof PsiClass)) return null;
      Map<PsiTypeParameter, PsiType> resurrected = new HashMap<PsiTypeParameter, PsiType>();
      final Set<Map.Entry<SmartPsiElementPointer, SmartTypePointer>> set = myMap.entrySet();
      for (Map.Entry<SmartPsiElementPointer, SmartTypePointer> entry : set) {
        PsiElement element = entry.getKey().getElement();
        if (element instanceof PsiTypeParameter) {
          resurrected.put((PsiTypeParameter)element, entry.getValue().getType());
        }
      }
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable((PsiClass)classElement)) {
        if (!resurrected.containsKey(typeParameter)) {
          resurrected.put(typeParameter, null);
        }
      }
      final PsiSubstitutor resurrectedSubstitutor = PsiSubstitutorImpl.createSubstitutor(resurrected);
      myType = new PsiImmediateClassType((PsiClass)classElement, resurrectedSubstitutor);
      return myType;
    }
  }

  private class ClassReferenceTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final SmartPsiElementPointer mySmartPsiElementPointer;
    private final String myReferenceText;

    ClassReferenceTypePointer(PsiClassReferenceType type) {
      myType = type;
      final PsiJavaCodeReferenceElement reference = type.getReference();
      mySmartPsiElementPointer = myPsiPointerManager.createSmartPsiElementPointer(reference);
      myReferenceText = reference.getText();
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)mySmartPsiElementPointer.getElement();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      if (referenceElement != null) {
        myType = factory.createType(referenceElement);
      }
      else {
        try {
          myType = factory.createTypeFromText(myReferenceText, null);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      return myType;
    }
  }

  private class DisjunctionTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final List<SmartTypePointer> myPointers;

    private DisjunctionTypePointer(final PsiDisjunctionType type) {
      myType = type;
      myPointers = ContainerUtil.map(type.getDisjunctions(), new Function<PsiType, SmartTypePointer>() {
        @Override public SmartTypePointer fun(PsiType psiType) { return createSmartTypePointer(psiType); }
      });
    }

    @Override
    public PsiType getType() {
      if (myType.isValid()) return myType;

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
      return new ArrayTypePointer(arrayType, arrayType.getComponentType().accept(this));
    }

    @Override
    public SmartTypePointer visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      final SmartTypePointer boundPointer;
      if (bound == null) {
        boundPointer = null;
      }
      else {
        boundPointer = bound.accept(this);
      }
      return new WildcardTypePointer(wildcardType, boundPointer);
    }

    @Override
    public SmartTypePointer visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) {
        if (classType instanceof PsiClassReferenceType) {
          return new ClassReferenceTypePointer((PsiClassReferenceType)classType);
        }
        return new SimpleTypePointer(classType);
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
}