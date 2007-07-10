/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class CollectionChildDescriptionImpl extends DomChildDescriptionImpl implements DomCollectionChildDescription {
  private final Collection<JavaMethod> myGetterMethods;
  private final Collection<JavaMethod> myAdderMethods;
  private final Collection<JavaMethod> myIndexedAdderMethods;
  private final Collection<JavaMethod> myClassAdderMethods;
  private final Collection<JavaMethod> myIndexedClassAdderMethods;
  private final Collection<JavaMethod> myInvertedIndexedClassAdderMethods;
  @NonNls private static final String ES = "es";

  public CollectionChildDescriptionImpl(final XmlName tagName,
                                        final Type type,
                                        final Collection<JavaMethod> adderMethods,
                                        final Collection<JavaMethod> classAdderMethods,
                                        final Collection<JavaMethod> getterMethods,
                                        final Collection<JavaMethod> indexedAdderMethods,
                                        final Collection<JavaMethod> indexedClassAdderMethods,
                                        final Collection<JavaMethod> invertedIndexedClassAdderMethods) {
    super(tagName, type);
    myAdderMethods = adderMethods;
    myClassAdderMethods = classAdderMethods;
    myGetterMethods = getterMethods;
    myIndexedAdderMethods = indexedAdderMethods;
    myIndexedClassAdderMethods = indexedClassAdderMethods;
    myInvertedIndexedClassAdderMethods = invertedIndexedClassAdderMethods;
  }

  public JavaMethod getClassAdderMethod() {
    return getFirst(myClassAdderMethods);
  }

  @Nullable
  private static JavaMethod getFirst(final Collection<JavaMethod> methods) {
    return methods.isEmpty() ? null : methods.iterator().next();
  }

  @Nullable
  public JavaMethod getIndexedClassAdderMethod() {
    return getFirst(myIndexedClassAdderMethods);
  }

  @Nullable
  public JavaMethod getInvertedIndexedClassAdderMethod() {
    return getFirst(myInvertedIndexedClassAdderMethods);
  }

  @Nullable
  public JavaMethod getAdderMethod() {
    return getFirst(myAdderMethods);
  }

  public DomElement addValue(DomElement element) {
    return addChild(element, getType(), Integer.MAX_VALUE);
  }

  private DomElement addChild(final DomElement element, final Type type, final int index) {
    try {
      final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
      assert handler != null;
      return handler.addChild(this, type, index);
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public DomElement addValue(DomElement element, int index) {
    return addChild(element, getType(), index);
  }

  public DomElement addValue(DomElement parent, Type type) {
    return addValue(parent, type, Integer.MAX_VALUE);
  }

  public final DomElement addValue(DomElement parent, Type type, int index) {
    return addChild(parent, type, Integer.MAX_VALUE);
  }

  @Nullable
  public final JavaMethod getGetterMethod() {
    final Collection<JavaMethod> methods = myGetterMethods;
    return methods.isEmpty() ? null : methods.iterator().next();
  }

  public JavaMethod getIndexedAdderMethod() {
    return getFirst(myIndexedAdderMethods);
  }

  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomElement element) {
    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
    if (handler != null) {
      return handler.getCollectionChildren(this);
    }
    final JavaMethod getterMethod = getGetterMethod();
    if (getterMethod == null) {
      final Collection<DomElement> collection = ModelMergerUtil.getFilteredImplementations(element);
      return ContainerUtil.concat(collection, new Function<DomElement, Collection<? extends DomElement>>() {
        public Collection<? extends DomElement> fun(final DomElement domElement) {
          final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(domElement);
          assert handler != null : domElement;
          return handler.getCollectionChildren(CollectionChildDescriptionImpl.this);
        }
      });
    }
    return (List<? extends DomElement>)getterMethod.invoke(element, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  @NotNull
  public String getCommonPresentableName(@NotNull DomNameStrategy strategy) {
    String words = strategy.splitIntoWords(getXmlElementName());
    return StringUtil.capitalizeWords(words.endsWith(ES) ? words: StringUtil.pluralize(words), true);
  }

  @Nullable
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final JavaMethod method = getGetterMethod();
    if (method != null) {
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null) return annotation;
    }

    final Type elemType = getType();
    return elemType instanceof AnnotatedElement ? ((AnnotatedElement)elemType).getAnnotation(annotationClass) : super.getAnnotation(annotationClass);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final CollectionChildDescriptionImpl that = (CollectionChildDescriptionImpl)o;

    if (myAdderMethods != null ? !myAdderMethods.equals(that.myAdderMethods) : that.myAdderMethods != null) return false;
    if (myClassAdderMethods != null ? !myClassAdderMethods.equals(that.myClassAdderMethods) : that.myClassAdderMethods != null) return false;
    if (myGetterMethods != null ? !myGetterMethods.equals(that.myGetterMethods) : that.myGetterMethods != null) return false;
    if (myIndexedAdderMethods != null ? !myIndexedAdderMethods.equals(that.myIndexedAdderMethods) : that.myIndexedAdderMethods != null) {
      return false;
    }
    if (myIndexedClassAdderMethods != null
        ? !myIndexedClassAdderMethods.equals(that.myIndexedClassAdderMethods)
        : that.myIndexedClassAdderMethods != null) {
      return false;
    }
    if (myInvertedIndexedClassAdderMethods != null
        ? !myInvertedIndexedClassAdderMethods.equals(that.myInvertedIndexedClassAdderMethods)
        : that.myInvertedIndexedClassAdderMethods != null) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + (myGetterMethods != null ? myGetterMethods.hashCode() : 0);
    result = 29 * result + (myAdderMethods != null ? myAdderMethods.hashCode() : 0);
    result = 29 * result + (myIndexedAdderMethods != null ? myIndexedAdderMethods.hashCode() : 0);
    result = 29 * result + (myClassAdderMethods != null ? myClassAdderMethods.hashCode() : 0);
    result = 29 * result + (myIndexedClassAdderMethods != null ? myIndexedClassAdderMethods.hashCode() : 0);
    result = 29 * result + (myInvertedIndexedClassAdderMethods != null ? myInvertedIndexedClassAdderMethods.hashCode() : 0);
    return result;
  }

  public Collection<JavaMethod> getAdderMethods() {
    return myAdderMethods;
  }

  public Collection<JavaMethod> getClassAdderMethods() {
    return myClassAdderMethods;
  }

  public Collection<JavaMethod> getGetterMethods() {
    return myGetterMethods;
  }

  public Collection<JavaMethod> getIndexedAdderMethods() {
    return myIndexedAdderMethods;
  }

  public Collection<JavaMethod> getIndexedClassAdderMethods() {
    return myIndexedClassAdderMethods;
  }

  public Collection<JavaMethod> getInvertedIndexedClassAdderMethods() {
    return myInvertedIndexedClassAdderMethods;
  }
}
