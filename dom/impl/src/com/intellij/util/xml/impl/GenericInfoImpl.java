/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.Function;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class GenericInfoImpl implements DomGenericInfo {
  private final Class<? extends DomElement> myClass;
  private final DomManagerImpl myDomManager;
  private final BidirectionalMap<JavaMethodSignature, Pair<XmlName, Integer>> myFixedChildrenMethods =
    new BidirectionalMap<JavaMethodSignature, Pair<XmlName, Integer>>();
  private final Map<XmlName, Integer> myFixedChildrenCounts = new THashMap<XmlName, Integer>();
  private final Map<JavaMethodSignature, XmlName> myCollectionChildrenGetterMethods = new THashMap<JavaMethodSignature, XmlName>();
  private final Map<JavaMethodSignature, XmlName> myCollectionChildrenAdditionMethods = new THashMap<JavaMethodSignature, XmlName>();
  private final Map<XmlName, Type> myCollectionChildrenClasses = new THashMap<XmlName, Type>();
  private final Map<JavaMethodSignature, XmlName> myAttributeChildrenMethods = new THashMap<JavaMethodSignature, XmlName>();
  private final Map<JavaMethodSignature, Set<XmlName>> myCompositeChildrenMethods = new THashMap<JavaMethodSignature, Set<XmlName>>();
  private final Map<JavaMethodSignature, Pair<XmlName, Set<XmlName>>> myCompositeCollectionAdditionMethods =
    new THashMap<JavaMethodSignature, Pair<XmlName, Set<XmlName>>>();
  @Nullable private JavaMethod myNameValueGetter;
  private boolean myValueElement;
  private boolean myInitialized;
  private static final Set ADDER_PARAMETER_TYPES = new THashSet<Class>(Arrays.asList(Class.class, int.class));

  public GenericInfoImpl(final Class aClass, final DomManagerImpl domManager) {
    myClass = aClass;
    myDomManager = domManager;
  }

  final int getFixedChildrenCount(XmlName qname) {
    final Integer integer = myFixedChildrenCounts.get(qname);
    return integer == null ? 0 : integer;
  }

  final JavaMethodSignature getFixedChildGetter(final Pair<XmlName, Integer> pair) {
    return myFixedChildrenMethods.getKeysByValue(pair).get(0);
  }

  final Set<Map.Entry<JavaMethodSignature, XmlName>> getCollectionChildrenEntries() {
    return myCollectionChildrenGetterMethods.entrySet();
  }

  final Type getCollectionChildrenType(XmlName tagName) {
    return myCollectionChildrenClasses.get(tagName);
  }

  final Set<Map.Entry<JavaMethodSignature, XmlName>> getAttributeChildrenEntries() {
    return myAttributeChildrenMethods.entrySet();
  }

  final Set<XmlName> getFixedChildrenNames() {
    return myFixedChildrenCounts.keySet();
  }

  final Set<XmlName> getCollectionChildrenNames() {
    return myCollectionChildrenClasses.keySet();
  }

  final Collection<XmlName> getAttributeChildrenNames() {
    return myAttributeChildrenMethods.values();
  }

  final Pair<XmlName, Integer> getFixedChildInfo(JavaMethodSignature method) {
    return myFixedChildrenMethods.get(method);
  }

  final XmlName getAttributeName(JavaMethodSignature method) {
    return myAttributeChildrenMethods.get(method);
  }

  private static boolean isCoreMethod(final JavaMethod method) {
    if (method.getSignature().findMethod(DomElement.class) != null) return true;

    final Class<?> aClass = method.getDeclaringClass();
    return aClass.equals(GenericAttributeValue.class) || aClass.equals(GenericDomValue.class) && "getConverter".equals(method.getName());
  }

  @Nullable
  private String getSubTagName(final JavaMethodSignature method) {
    final SubTag subTagAnnotation = method.findAnnotation(SubTag.class, myClass);
    if (subTagAnnotation == null || StringUtil.isEmpty(subTagAnnotation.value())) {
      return getNameFromMethod(method, false);
    }
    return subTagAnnotation.value();
  }

  @Nullable
  private String getSubTagNameForCollection(final JavaMethodSignature method) {
    final SubTagList subTagList = method.findAnnotation(SubTagList.class, myClass);
    if (subTagList == null || StringUtil.isEmpty(subTagList.value())) {
      final String propertyName = getPropertyName(method);
      return propertyName != null ? getNameStrategy(false).convertName(StringUtil.unpluralize(propertyName)) : null;
    }
    return subTagList.value();
  }

  @Nullable
  private String getNameFromMethod(final JavaMethodSignature method, boolean isAttribute) {
    final String propertyName = getPropertyName(method);
    return propertyName == null ? null : getNameStrategy(isAttribute).convertName(propertyName);
  }

  @Nullable
  private static String getPropertyName(JavaMethodSignature method) {
    return PropertyUtil.getPropertyName(method.getMethodName());
  }

  @NotNull
  private DomNameStrategy getNameStrategy(boolean isAttribute) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(ReflectionUtil.getRawType(myClass), isAttribute);
    return strategy != null ? strategy : DomNameStrategy.HYPHEN_STRATEGY;
  }

  public final synchronized void buildMethodMaps() {
    if (!myInitialized) {
      doBuildMethodMaps();
      myInitialized = true;
    }
  }

  private void doBuildMethodMaps() {
    final Set<JavaMethod> methods = new THashSet<JavaMethod>();
    for (final Method method : ReflectionCache.getMethods(myClass)) {
      methods.add(JavaMethod.getMethod(myClass, method));
    }
    {
      final Class implClass = myDomManager.getImplementation(myClass);
      if (implClass != null) {
        for (Method method : ReflectionCache.getMethods(implClass)) {
          if (!Modifier.isAbstract(method.getModifiers())) {
            final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
            if (signature.findMethod(myClass) != null) {
              methods.remove(JavaMethod.getMethod(myClass, signature));
            }
          }
        }
      }
    }

    for (Iterator<JavaMethod> iterator = methods.iterator(); iterator.hasNext();) {
      final JavaMethod method = iterator.next();
      if (isCoreMethod(method) || DomImplUtil.isTagValueSetter(method) || method.getAnnotation(PropertyAccessor.class) != null) {
        if (method.getAnnotation(NameValue.class) != null) {
          myNameValueGetter = method;
        }
        iterator.remove();
      }
    }

    for (Iterator<JavaMethod> iterator = methods.iterator(); iterator.hasNext();) {
      final JavaMethod method = iterator.next();
      if (DomImplUtil.isGetter(method) && processGetterMethod(method)) {
        if (method.getAnnotation(NameValue.class) != null) {
          myNameValueGetter = method;
        }
        iterator.remove();
      }
    }

    for (Iterator<JavaMethod> iterator = methods.iterator(); iterator.hasNext();) {
      final JavaMethod method = iterator.next();
      final SubTagsList subTagsList = method.getAnnotation(SubTagsList.class);
      if (subTagsList != null && method.getName().startsWith("add")) {
        final XmlName tagName = new XmlName(subTagsList.tagName());
        assert StringUtil.isNotEmpty(tagName.getLocalName());
        final Set<XmlName> set = getXmlNames(subTagsList, method.getGenericReturnType(), method);
        assert set.contains(tagName);
        myCompositeCollectionAdditionMethods.put(method.getSignature(), Pair.create(tagName, set));
        iterator.remove();
      }
      else if (isAddMethod(method)) {
        myCollectionChildrenAdditionMethods.put(method.getSignature(), extractTagName(method, "add"));
        iterator.remove();
      }
    }

    if (false) {
      if (!methods.isEmpty()) {
        StringBuilder sb = new StringBuilder(myClass + " should provide the following implementations:");
        for (JavaMethod method : methods) {
          sb.append("\n  ");
          sb.append(method);
        }
        assert false : sb.toString();
        //System.out.println(sb.toString());
      }
    }
  }

  private boolean isAddMethod(JavaMethod method) {
    final XmlName tagName = extractTagName(method, "add");
    if (tagName == null) return false;

    final Type childrenClass = getCollectionChildrenType(tagName);
    if (childrenClass == null || !ReflectionUtil.getRawType(childrenClass).isAssignableFrom(method.getReturnType())) return false;

    return ADDER_PARAMETER_TYPES.containsAll(Arrays.asList(method.getParameterTypes()));
  }

  @Nullable
  private XmlName extractTagName(JavaMethod method, @NonNls String prefix) {
    final String name = method.getName();
    if (!name.startsWith(prefix)) return null;

    final SubTagList subTagAnnotation = method.getAnnotation(SubTagList.class);
    if (subTagAnnotation != null && !StringUtil.isEmpty(subTagAnnotation.value())) {
      return XmlName.create(subTagAnnotation.value(), method);
    }

    final String tagName = getNameStrategy(false).convertName(name.substring(prefix.length()));
    return StringUtil.isEmpty(tagName) ? null : XmlName.create(tagName, method);
  }

  private boolean processGetterMethod(final JavaMethod method) {
    if (DomImplUtil.isTagValueGetter(method)) {
      myValueElement = true;
      return true;
    }

    final boolean isAttributeValueMethod = method.getReturnType().equals(GenericAttributeValue.class);
    final JavaMethodSignature signature = method.getSignature();
    final Attribute annotation = signature.findAnnotation(Attribute.class, myClass);
    final boolean isAttributeMethod = annotation != null || isAttributeValueMethod;
    if (annotation != null) {
      assert
        isAttributeValueMethod || method.getReturnType().isAssignableFrom(GenericAttributeValue.class) :
        method + " should return " + GenericAttributeValue.class;
    }
    if (isAttributeMethod) {
      final String s = annotation == null ? null : annotation.value();
      String attributeName = StringUtil.isEmpty(s) ? getNameFromMethod(signature, true) : s;
      assert attributeName != null && StringUtil.isNotEmpty(attributeName) : "Can't guess attribute name from method name: " + method.getName();
      myAttributeChildrenMethods.put(signature, XmlName.create(attributeName, method));
      return true;
    }

    if (isDomElement(method.getReturnType())) {
      final String qname = getSubTagName(signature);
      if (qname != null) {
        final XmlName xmlName = XmlName.create(qname, method);
        assert !isCollectionChild(xmlName) : "Collection and fixed children cannot intersect: " + qname;
        int index = 0;
        final SubTag subTagAnnotation = signature.findAnnotation(SubTag.class, myClass);
        if (subTagAnnotation != null && subTagAnnotation.index() != 0) {
          index = subTagAnnotation.index();
        }
        myFixedChildrenMethods.put(signature, Pair.create(xmlName, index));
        final Integer integer = myFixedChildrenCounts.get(xmlName);
        if (integer == null || integer < index + 1) {
          myFixedChildrenCounts.put(xmlName, index + 1);
        }
        return true;
      }
    }

    final Type type = DomReflectionUtil.extractCollectionElementType(method.getGenericReturnType());
    if (isDomElement(type)) {
      final SubTagsList subTagsList = method.getAnnotation(SubTagsList.class);
      if (subTagsList != null) {
        myCompositeChildrenMethods.put(signature, getXmlNames(subTagsList, type, method));
        return true;
      }

      final String qname = getSubTagNameForCollection(signature);
      if (qname != null) {
        XmlName xmlName = XmlName.create(qname, type, method);
        assert !isFixedChild(xmlName) : "Collection and fixed children cannot intersect: " + qname;
        myCollectionChildrenClasses.put(xmlName, type);
        myCollectionChildrenGetterMethods.put(signature, xmlName);
        return true;
      }
    }

    return false;
  }

  private static Set<XmlName> getXmlNames(final SubTagsList subTagsList, final Type type, final JavaMethod method) {
    return ContainerUtil.map2Set(subTagsList.value(), new Function<String, XmlName>() {
      public XmlName fun(String s) {
        return XmlName.create(s, type, method);
      }
    });
  }

  public final Invocation createInvocation(final JavaMethod method) {
    buildMethodMaps();

    final JavaMethodSignature signature = method.getSignature();
    final PropertyAccessor accessor = signature.findAnnotation(PropertyAccessor.class, myClass);
    if (accessor != null) {
      return new PropertyAccessorInvocation(DomReflectionUtil.getGetterMethods(accessor.value(), myClass));
    }

    if (myAttributeChildrenMethods.containsKey(signature)) {
      return new GetAttributeChildInvocation(signature);
    }

    if (myFixedChildrenMethods.containsKey(signature)) {
      return new GetFixedChildInvocation(signature);
    }

    final Set<XmlName> qnames = myCompositeChildrenMethods.get(signature);
    if (qnames != null) {
      return new GetCompositeCollectionInvocation(qnames);
    }

    final Pair<XmlName, Set<XmlName>> pair = myCompositeCollectionAdditionMethods.get(signature);
    if (pair != null) {
      return new AddToCompositeCollectionInvocation(pair.first, pair.second, method.getGenericReturnType());
    }

    XmlName qname = myCollectionChildrenGetterMethods.get(signature);
    if (qname != null) {
      return new GetCollectionChildInvocation(qname);
    }

    qname = myCollectionChildrenAdditionMethods.get(signature);
    if (qname != null) {
      return new AddChildInvocation(getTypeGetter(method), getIndexGetter(method), qname, myCollectionChildrenClasses.get(qname));
    }

    throw new UnsupportedOperationException("No implementation for method " + method.toString() + " in class " + myClass);
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
  private JavaMethod findGetterMethod(final Map<JavaMethodSignature, XmlName> map, final XmlName xmlElementName) {
    buildMethodMaps();
    for (Map.Entry<JavaMethodSignature, XmlName> entry : map.entrySet()) {
      if (xmlElementName.equals(entry.getValue())) {
        return JavaMethod.getMethod(myClass, entry.getKey());
      }
    }
    return null;
  }

  @Nullable
  private JavaMethod getCollectionAddMethod(final XmlName tagName, Class... parameterTypes) {
    for (Map.Entry<JavaMethodSignature, XmlName> entry : myCollectionChildrenAdditionMethods.entrySet()) {
      if (tagName.equals(entry.getValue())) {
        final JavaMethodSignature method = entry.getKey();
        if (Arrays.equals(parameterTypes, method.getParameterTypes())) {
          return JavaMethod.getMethod(myClass, method);
        }
      }
    }
    return null;
  }

  private JavaMethod[] getFixedChildrenGetterMethods(XmlName tagName) {
    final JavaMethod[] methods = new JavaMethod[getFixedChildrenCount(tagName)];
    for (Map.Entry<JavaMethodSignature, Pair<XmlName, Integer>> entry : myFixedChildrenMethods.entrySet()) {
      final Pair<XmlName, Integer> pair = entry.getValue();
      if (tagName.equals(pair.getFirst())) {
        methods[pair.getSecond()] = JavaMethod.getMethod(myClass, entry.getKey());
      }
    }
    return methods;
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

  @NotNull
  public final List<DomChildDescriptionImpl> getChildrenDescriptions() {
    final ArrayList<DomChildDescriptionImpl> result = new ArrayList<DomChildDescriptionImpl>();
    result.addAll(getAttributeChildrenDescriptions());
    result.addAll(getFixedChildrenDescriptions());
    result.addAll(getCollectionChildrenDescriptions());
    return result;
  }

  @NotNull
  public List<FixedChildDescriptionImpl> getFixedChildrenDescriptions() {
    buildMethodMaps();
    final ArrayList<FixedChildDescriptionImpl> result = new ArrayList<FixedChildDescriptionImpl>();
    for (XmlName s : myFixedChildrenCounts.keySet()) {
      result.add(getFixedChildDescription(s));
    }
    return result;
  }

  @NotNull
  public List<CollectionChildDescriptionImpl> getCollectionChildrenDescriptions() {
    buildMethodMaps();
    final ArrayList<CollectionChildDescriptionImpl> result = new ArrayList<CollectionChildDescriptionImpl>();
    for (XmlName s : myCollectionChildrenClasses.keySet()) {
      result.add(getCollectionChildDescription(s));
    }
    return result;
  }

  @Deprecated
  @Nullable
  public FixedChildDescriptionImpl getFixedChildDescription(String tagName) {
    return findChildDescription(tagName, getFixedChildrenDescriptions());
  }

  @Nullable
  public DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespace) {
    return getFixedChildDescription(new XmlName(tagName, namespace));
  }

  @Nullable
  public FixedChildDescriptionImpl getFixedChildDescription(XmlName tagName) {
    buildMethodMaps();
    if (!isFixedChild(tagName)) {
      return null;
    }

    final JavaMethod[] getterMethods = getFixedChildrenGetterMethods(tagName);
    assert getterMethods.length > 0 : tagName + " " + myClass;
    return new FixedChildDescriptionImpl(tagName, getterMethods[0].getGenericReturnType(), getFixedChildrenCount(tagName), getterMethods);
  }

  @Deprecated
  @Nullable public CollectionChildDescriptionImpl getCollectionChildDescription(String tagName) {
    return findChildDescription(tagName, getCollectionChildrenDescriptions());
  }

  @Nullable
  public DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespace) {
    return getCollectionChildDescription(new XmlName(tagName, namespace));
  }

  @Nullable public CollectionChildDescriptionImpl getCollectionChildDescription(XmlName tagName) {
    buildMethodMaps();
    final JavaMethod getter = findGetterMethod(myCollectionChildrenGetterMethods, tagName);
    return getter == null ? null : new CollectionChildDescriptionImpl(tagName, getCollectionChildrenType(tagName), getCollectionAddMethod(tagName),
                                                                      getCollectionAddMethod(tagName, Class.class), getter,
                                                                      getCollectionAddMethod(tagName, int.class), getCollectionAddMethod(tagName, Class.class, int.class),
                                                                      getCollectionAddMethod(tagName, int.class, Class.class));
  }

  @Deprecated
  @Nullable public AttributeChildDescriptionImpl getAttributeChildDescription(String attributeName) {
    return findChildDescription(attributeName, getAttributeChildrenDescriptions());
  }

  private static <T extends DomChildrenDescription> T findChildDescription(final String localName, final List<T> list) {
    for (final T description : list) {
      if (description.getXmlElementName().equals(localName)) return description;
    }
    return null;
  }

  @Nullable
  public DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespace) {
    return getAttributeChildDescription(new XmlName(attributeName, namespace));
  }

  @Nullable public AttributeChildDescriptionImpl getAttributeChildDescription(XmlName attributeName) {
    final JavaMethod getter = findGetterMethod(myAttributeChildrenMethods, attributeName);
    if (getter == null) return null;
    return new AttributeChildDescriptionImpl(attributeName, getter);
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
    final ArrayList<AttributeChildDescriptionImpl> result = new ArrayList<AttributeChildDescriptionImpl>();
    for (Map.Entry<JavaMethodSignature, XmlName> entry : myAttributeChildrenMethods.entrySet()) {
      final JavaMethod getter = JavaMethod.getMethod(myClass, entry.getKey());
      result.add(new AttributeChildDescriptionImpl(entry.getValue(), getter));
    }
    return result;
  }

  @Nullable public DomChildrenDescription getChildDescription(String tagName) {
    return getChildDescription(new XmlName(tagName));
  }

  @Nullable public DomChildrenDescription getChildDescription(XmlName tagName) {
    if (isCollectionChild(tagName)) {
      return getCollectionChildDescription(tagName);
    }
    if (isFixedChild(tagName)) {
      return getFixedChildDescription(tagName);
    }
    return null;
  }

  final boolean isFixedChild(final XmlName qname) {
    return myFixedChildrenCounts.containsKey(qname);
  }

  final boolean isCollectionChild(final XmlName qname) {
    return myCollectionChildrenClasses.containsKey(qname);
  }

  private static boolean isDomElement(final Type type) {
    return type != null && DomElement.class.isAssignableFrom(ReflectionUtil.getRawType(type));
  }

  public final Set<String> getReferenceTagNames() {
    final HashSet<String> set = new HashSet<String>();
    addReferenceElementNames(new Condition<Class>() {
      public boolean value(final Class object) {
        return GenericValue.class.isAssignableFrom(object) && !GenericAttributeValue.class.isAssignableFrom(object);
      }
    }, set, new HashSet<GenericInfoImpl>());
    return set;
  }

  public final Set<String> getReferenceAttributeNames() {
    final HashSet<String> set = new HashSet<String>();
    addReferenceElementNames(new Condition<Class>() {
      public boolean value(final Class object) {
        return GenericAttributeValue.class.isAssignableFrom(object);
      }
    }, set, new HashSet<GenericInfoImpl>());
    return set;
  }


  private void addReferenceElementNames(final Condition<Class> condition, final HashSet<String> set, final HashSet<GenericInfoImpl> visited) {
    visited.add(this);
    Type[] classes = getConcreteInterfaceVariants();
    if (classes.length == 1 && classes[0].equals(myClass)) {
      for (final DomChildDescriptionImpl description : getChildrenDescriptions()) {
        final Convert annotation = description.getAnnotation(Convert.class);
        final boolean hasResolveConverter = annotation != null &&
                                            (ReflectionCache.isAssignable(ResolvingConverter.class, annotation.value()) ||
                                             ReflectionCache.isAssignable(CustomReferenceConverter.class, annotation.value()));
        final Type type = description.getType();
        if (condition.value(ReflectionUtil.getRawType(type))) {
          if (hasResolveConverter ||
              !String.class.equals(DomUtil.getGenericValueParameter(type)) ||
              description.getAnnotation(NameValue.class) != null ||
              description.getAnnotation(Referencing.class) != null) {
            set.add(description.getXmlElementName());
          }
        } else {
          final GenericInfoImpl childGenericInfo = description.getChildGenericInfo(myDomManager.getProject());
          if (!visited.contains(childGenericInfo)) {
            childGenericInfo.addReferenceElementNames(condition, set, visited);
          }
        }
      }
    } else {
      for (final Type aClass : classes) {
        final GenericInfoImpl info = myDomManager.getGenericInfo(aClass);
        if (!visited.contains(info)) {
          info.addReferenceElementNames(condition, set, visited);
        }
      }
    }
  }

  @Nullable
  public DomChildDescriptionImpl findChildrenDescription(DomInvocationHandler handler, final String localName, String namespace, boolean attribute, final String qName) {
    for (final DomChildDescriptionImpl description : getChildrenDescriptions()) {
      if (description instanceof AttributeChildDescriptionImpl == attribute) {
        final EvaluatedXmlName xmlName = description.getXmlName().createEvaluatedXmlName(handler);
        if (DomImplUtil.isNameSuitable(xmlName, localName, qName, namespace, handler)) {
          return description;
        }
      }
    }
    return null;
  }

}
