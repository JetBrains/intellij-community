/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class StaticGenericInfo {
  private final Class<? extends DomElement> myClass;
  private final DomManagerImpl myDomManager;

  private final ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> myAttributes = new ChildrenDescriptionsHolder<AttributeChildDescriptionImpl>();
  private final ChildrenDescriptionsHolder<FixedChildDescriptionImpl> myFixed = new ChildrenDescriptionsHolder<FixedChildDescriptionImpl>();
  private final ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> myCollections = new ChildrenDescriptionsHolder<CollectionChildDescriptionImpl>();

  private Map<JavaMethodSignature, Pair<FixedChildDescriptionImpl, Integer>> myFixedChildrenMethods;
  private Map<JavaMethodSignature, CollectionChildDescriptionImpl> myCollectionChildrenGetterMethods;
  private Map<JavaMethodSignature, CollectionChildDescriptionImpl> myCollectionChildrenAdditionMethods = new THashMap<JavaMethodSignature, CollectionChildDescriptionImpl>();
  private Map<JavaMethodSignature, AttributeChildDescriptionImpl> myAttributeChildrenMethods;

  private Map<JavaMethodSignature, Set<CollectionChildDescriptionImpl>> myCompositeChildrenMethods = new THashMap<JavaMethodSignature, Set<CollectionChildDescriptionImpl>>();
  private Map<JavaMethodSignature, Pair<CollectionChildDescriptionImpl, Set<CollectionChildDescriptionImpl>>> myCompositeCollectionAdditionMethods = new THashMap<JavaMethodSignature, Pair<CollectionChildDescriptionImpl, Set<CollectionChildDescriptionImpl>>>();

  @Nullable private JavaMethod myNameValueGetter;
  private boolean myValueElement;
  private boolean myInitialized;

  public StaticGenericInfo(final Class aClass, final DomManagerImpl domManager) {
    myClass = aClass;
    myDomManager = domManager;
  }

  public final synchronized void buildMethodMaps() {
    if (!myInitialized) {
      final StaticGenericInfoBuilder builder = new StaticGenericInfoBuilder(myDomManager, myClass);
      myAttributeChildrenMethods = builder.getAttributes();
      myAttributes.addDescriptions(myAttributeChildrenMethods.values());

      myFixedChildrenMethods = builder.getFixedGetters();
      for (final Pair<FixedChildDescriptionImpl, Integer> pair : myFixedChildrenMethods.values()) {
        myFixed.addDescription(pair.first);
      }

      myCollectionChildrenGetterMethods = builder.getCollectionGetters();
      myCollections.addDescriptions(myCollectionChildrenGetterMethods.values());


      for (final CollectionChildDescriptionImpl description : myCollectionChildrenGetterMethods.values()) {
        addAdder(description, description.getAdderMethods());
        addAdder(description, description.getIndexedAdderMethods());
        addAdder(description, description.getIndexedClassAdderMethods());
        addAdder(description, description.getInvertedIndexedClassAdderMethods());
        addAdder(description, description.getClassAdderMethods());
      }

      final NotNullFunction<String, CollectionChildDescriptionImpl> mapper = new NotNullFunction<String, CollectionChildDescriptionImpl>() {
        @NotNull
        public CollectionChildDescriptionImpl fun(final String xmlName) {
          return ObjectUtils.assertNotNull(myCollections.findDescription(xmlName));
        }
      };
      final Map<JavaMethodSignature, String[]> getters = builder.getCompositeCollectionGetters();
      for (final JavaMethodSignature signature : getters.keySet()) {
        myCompositeChildrenMethods.put(signature, ContainerUtil.map2Set(getters.get(signature), mapper));
      }
      final Map<JavaMethodSignature, Pair<String, String[]>> adders = builder.getCompositeCollectionAdders();
      for (final JavaMethodSignature signature : adders.keySet()) {
        final Pair<String, String[]> pair = adders.get(signature);
        myCompositeCollectionAdditionMethods.put(signature, Pair.create(myCollections.findDescription(pair.first), ContainerUtil.map2Set(pair.second, mapper)));
      }
      myNameValueGetter = builder.getNameValueGetter();
      myValueElement = builder.isValueElement();
      myInitialized = true;
    }
  }

  private void addAdder(final CollectionChildDescriptionImpl description, final Collection<JavaMethod> methods) {
    if (methods != null) {
      for (final JavaMethod method : methods) {
        myCollectionChildrenAdditionMethods.put(method.getSignature(), description);
      }
    }
  }


  public final Invocation createInvocation(final JavaMethod method) {
    buildMethodMaps();

    final JavaMethodSignature signature = method.getSignature();
    final PropertyAccessor accessor = signature.findAnnotation(PropertyAccessor.class, myClass);
    if (accessor != null) {
      return new PropertyAccessorInvocation(DomReflectionUtil.getGetterMethods(accessor.value(), myClass));
    }

    if (myAttributeChildrenMethods.containsKey(signature)) {
      return new GetAttributeChildInvocation(myAttributeChildrenMethods.get(signature));
    }

    if (myFixedChildrenMethods.containsKey(signature)) {
      return new GetFixedChildInvocation(myFixedChildrenMethods.get(signature));
    }

    final Set<CollectionChildDescriptionImpl> qnames = myCompositeChildrenMethods.get(signature);
    if (qnames != null) {
      return new GetCompositeCollectionInvocation(qnames);
    }

    final Pair<CollectionChildDescriptionImpl, Set<CollectionChildDescriptionImpl>> pair = myCompositeCollectionAdditionMethods.get(signature);
    if (pair != null) {
      return new AddToCompositeCollectionInvocation(pair.first, pair.second, method.getGenericReturnType());
    }

    CollectionChildDescriptionImpl description = myCollectionChildrenGetterMethods.get(signature);
    if (description != null) {
      return new GetCollectionChildInvocation(description);
    }

    description = myCollectionChildrenAdditionMethods.get(signature);
    if (description != null) {
      return new AddChildInvocation(getTypeGetter(method), getIndexGetter(method), description, description.getType());
    }

    throw new RuntimeException("No implementation for method " + method.toString() + " in class " + myClass);
  }

  private static Function<Object[], Type> getTypeGetter(final JavaMethod method) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length >= 1 && parameterTypes[0].equals(Class.class)) {
      return new Function<Object[], Type>() {
        public Type fun(final Object[] s) {
          return (Type)s[0];
        }
      };
    }

    if (parameterTypes.length == 2 && parameterTypes[1].equals(Class.class)) {
      return new Function<Object[], Type>() {
        public Type fun(final Object[] s) {
          return (Type)s[1];
        }
      };
    }

    return new Function<Object[], Type>() {
      public Type fun(final Object[] s) {
        return method.getGenericReturnType();
      }
    };
  }


  private static Function<Object[], Integer> getIndexGetter(final JavaMethod method) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length >= 1 && parameterTypes[0].equals(int.class)) {
      return new Function<Object[], Integer>() {
        public Integer fun(final Object[] s) {
          return (Integer)s[0];
        }
      };
    }

    if (parameterTypes.length == 2 && parameterTypes[1].equals(int.class)) {
      return new Function<Object[], Integer>() {
        public Integer fun(final Object[] s) {
          return (Integer)s[1];
        }
      };
    }

    return new Function<Object[], Integer>() {
      public Integer fun(final Object[] s) {
        return Integer.MAX_VALUE;
      }
    };
  }

  @Nullable
  public XmlElement getNameElement(DomElement element) {
    Object o = getNameObject(element);
    if (o instanceof GenericAttributeValue) {
      return ((GenericAttributeValue)o).getXmlAttributeValue();
    } else if (o instanceof DomElement) {
      return ((DomElement)o).getXmlTag();
    }
    else {
      return null;
    }
  }

  @Nullable
  public GenericDomValue getNameDomElement(DomElement element) {
    Object o = getNameObject(element);
    return o instanceof GenericDomValue ? (GenericDomValue)o : null;
  }

  @Nullable
  private Object getNameObject(DomElement element) {
    return myNameValueGetter == null ? null : myNameValueGetter.invoke(element);
  }

  @Nullable
  public String getElementName(DomElement element) {
    Object o = getNameObject(element);
    return o == null || o instanceof String ? (String)o : ((GenericValue)o).getStringValue();
  }

  @Nullable public CollectionChildDescriptionImpl getCollectionChildDescription(XmlName tagName) {
    buildMethodMaps();
    return myCollections.getDescription(tagName);
  }

  public final Type[] getConcreteInterfaceVariants() {
    return myDomManager.getTypeChooserManager().getTypeChooser(myClass).getChooserTypes();
  }

  public boolean isTagValueElement() {
    buildMethodMaps();
    return myValueElement;
  }

  @NotNull
  public List<AttributeChildDescriptionImpl> getAttributeChildrenDescriptions() {
    buildMethodMaps();
    return new ArrayList<AttributeChildDescriptionImpl>(myAttributeChildrenMethods.values());
  }

  public ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> getAttributes() {
    return myAttributes;
  }

  public ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> getCollections() {
    return myCollections;
  }

  public ChildrenDescriptionsHolder<FixedChildDescriptionImpl> getFixed() {
    return myFixed;
  }
}
