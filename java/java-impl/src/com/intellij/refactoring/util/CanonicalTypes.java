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
package com.intellij.refactoring.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author dsl
 */
public class CanonicalTypes {
  private CanonicalTypes() { }

  public abstract static class Type {
    @NotNull
    public abstract PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException;

    @NonNls
    public abstract String getTypeText();

    public void addImportsTo(JavaCodeFragment fragment) { }

    public boolean isValid() {
      return true;
    }
  }

  private abstract static class AnnotatedType extends Type {
    protected final PsiAnnotation[] myAnnotations;

    protected AnnotatedType(PsiAnnotation[] annotations) {
      myAnnotations = annotations;
    }
  }

  private static class Primitive extends AnnotatedType {
    private final PsiPrimitiveType myType;

    private Primitive(PsiPrimitiveType type) {
      super(type.getAnnotations());
      myType = type;
    }

    @NotNull
    @Override
    public PsiType getType(PsiElement context, PsiManager manager) {
      return myAnnotations.length == 0 ? myType : new PsiPrimitiveType(myType.getCanonicalText(false), myAnnotations);
    }

    @Override
    public String getTypeText() {
      return myType.getPresentableText();
    }
  }

  private static class Array extends AnnotatedType {
    protected final Type myComponentType;

    private Array(PsiType original, Type componentType) {
      super(original.getAnnotations());
      myComponentType = componentType;
    }

    @NotNull
    @Override
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      return myComponentType.getType(context, manager).createArrayType(myAnnotations);
    }

    @Override
    public String getTypeText() {
      return myComponentType.getTypeText() + "[]";
    }

    @Override
    public void addImportsTo(JavaCodeFragment fragment) {
      myComponentType.addImportsTo(fragment);
    }

    @Override
    public boolean isValid() {
      return myComponentType.isValid();
    }
  }

  private static class Ellipsis extends Array {
    private Ellipsis(PsiType original, Type componentType) {
      super(original, componentType);
    }

    @NotNull
    @Override
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      return new PsiEllipsisType(myComponentType.getType(context, manager), myAnnotations);
    }

    @Override
    public String getTypeText() {
      return myComponentType.getTypeText() + "...";
    }
  }

  private static class WildcardType extends AnnotatedType {
    private final boolean myIsExtending;
    private final Type myBound;

    private WildcardType(PsiType original, boolean isExtending, Type bound) {
      super(original.getAnnotations());
      myIsExtending = isExtending;
      myBound = bound;
    }

    @NotNull
    @Override
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      PsiWildcardType type;
      if (myBound == null) {
        type = PsiWildcardType.createUnbounded(manager);
      }
      else if (myIsExtending) {
        type = PsiWildcardType.createExtends(manager, myBound.getType(context, manager));
      }
      else {
        type = PsiWildcardType.createSuper(manager, myBound.getType(context, manager));
      }
      return type.annotate(myAnnotations);
    }

    @Override
    public String getTypeText() {
      if (myBound == null) {
        return "?";
      }
      else {
        return "? " + (myIsExtending ? "extends " : "super ") + myBound.getTypeText();
      }
    }

    @Override
    public void addImportsTo(JavaCodeFragment fragment) {
      if (myBound != null) {
        myBound.addImportsTo(fragment);
      }
    }

    @Override
    public boolean isValid() {
      return myBound == null || myBound.isValid();
    }
  }

  private static class UnresolvedType extends Type {
    private final String myPresentableText;
    private final String myCanonicalText;

    private UnresolvedType(PsiType original) {
      myPresentableText = original.getPresentableText();
      myCanonicalText = original.getCanonicalText(true);
    }

    @NotNull
    @Override
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeFromText(myCanonicalText, context);
    }

    @Override
    public String getTypeText() {
      return myPresentableText;
    }

    @Override
    public boolean isValid() {
      return false;
    }
  }

  private static class ClassType extends AnnotatedType {
    private final String myPresentableText;
    private final String myClassQName;
    private final Map<String, Type> mySubstitutor;

    private ClassType(PsiType original, String classQName, Map<String, Type> substitutor) {
      super(original.getAnnotations());
      myPresentableText = original.getPresentableText();
      myClassQName = classQName;
      mySubstitutor = substitutor;
    }

    @NotNull
    @Override
    public PsiType getType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      PsiElementFactory factory = facade.getElementFactory();

      PsiClass aClass = facade.getResolveHelper().resolveReferencedClass(myClassQName, context);
      if (aClass == null) {
        return factory.createTypeFromText(myClassQName, context);
      }

      Map<PsiTypeParameter, PsiType> substitutionMap = ContainerUtil.newHashMap();
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
        Type substitute = mySubstitutor.get(typeParameter.getName());
        substitutionMap.put(typeParameter, substitute != null ? substitute.getType(context, manager) : null);
      }
      return factory.createType(aClass, factory.createSubstitutor(substitutionMap), null, myAnnotations);
    }

    @Override
    public String getTypeText() {
      return myPresentableText;
    }

    @Override
    public void addImportsTo(JavaCodeFragment fragment) {
      fragment.addImportsFromString(myClassQName);
      for (Type type : mySubstitutor.values()) {
        if (type != null) {
          type.addImportsTo(fragment);
        }
      }
    }
  }

  private static class DisjunctionType extends Type {
    private final List<Type> myTypes;

    private DisjunctionType(List<Type> types) {
      myTypes = types;
    }

    @NotNull
    @Override
    public PsiType getType(final PsiElement context, final PsiManager manager) throws IncorrectOperationException {
      List<PsiType> types = ContainerUtil.map(myTypes, new Function<Type, PsiType>() {
        @Override
        public PsiType fun(Type type) {
          return type.getType(context, manager);
        }
      });
      return new PsiDisjunctionType(types, manager);
    }

    @Override
    public String getTypeText() {
      return StringUtil.join(myTypes, new Function<Type, String>() {
        @Override
        public String fun(Type type) {
          return type.getTypeText();
        }
      }, "|");
    }

    @Override
    public void addImportsTo(JavaCodeFragment fragment) {
      for (Type type : myTypes) {
        type.addImportsTo(fragment);
      }
    }
  }

  private static class Creator extends PsiTypeVisitor<Type> {
    public static final Creator INSTANCE = new Creator();

    @Override
    public Type visitPrimitiveType(PsiPrimitiveType type) {
      return new Primitive(type);
    }

    @Override
    public Type visitEllipsisType(PsiEllipsisType type) {
      return new Ellipsis(type, type.getComponentType().accept(this));
    }

    @Override
    public Type visitArrayType(PsiArrayType type) {
      return new Array(type, type.getComponentType().accept(this));
    }

    @Override
    public Type visitWildcardType(PsiWildcardType type) {
      PsiType bound = type.getBound();
      return new WildcardType(type, type.isExtends(), bound == null ? null : bound.accept(this));
    }

    @Override
    public Type visitClassType(PsiClassType type) {
      PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
      PsiClass aClass = resolveResult.getElement();
      if (aClass instanceof PsiAnonymousClass) {
        return visitClassType(((PsiAnonymousClass)aClass).getBaseClassType());
      }
      else if (aClass == null) {
        return new UnresolvedType(type);
      }
      else {
        Map<String, Type> substitutionMap = ContainerUtil.newHashMap();
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
          PsiType substitute = substitutor.substitute(typeParameter);
          substitutionMap.put(typeParameter.getName(), substitute != null ? substitute.accept(this) : null);
        }
        String qualifiedName = ObjectUtils.notNull(aClass.getQualifiedName(), aClass.getName());
        return new ClassType(type, qualifiedName, substitutionMap);
      }
    }

    @Override
    public Type visitDisjunctionType(PsiDisjunctionType type) {
      List<Type> types = ContainerUtil.map(type.getDisjunctions(), new Function<PsiType, Type>() {
        @Override
        public Type fun(PsiType type) {
          return type.accept(Creator.this);
        }
      });
      return new DisjunctionType(types);
    }
  }

  public static Type createTypeWrapper(@NotNull PsiType type) {
    return type.accept(Creator.INSTANCE);
  }
}
