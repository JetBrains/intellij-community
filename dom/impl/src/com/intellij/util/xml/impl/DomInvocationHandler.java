/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.CollectionElementAddedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import com.intellij.util.xml.events.TagValueChangeEvent;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author peter                  
 */
public abstract class DomInvocationHandler extends UserDataHolderBase implements InvocationHandler, DomElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomInvocationHandler");
  private static final EvaluatedXmlName ATTRIBUTES = new EvaluatedXmlName(new XmlName("@"), null);
  public static final Method ACCEPT_METHOD = ReflectionUtil.getMethod(DomElement.class, "accept", DomElementVisitor.class);
  public static final Method ACCEPT_CHILDREN_METHOD = ReflectionUtil.getMethod(DomElement.class, "acceptChildren", DomElementVisitor.class);
  protected static final Method CREATE_STABLE_COPY_METHOD = ReflectionUtil.getMethod(DomElement.class, "createStableCopy");

  private final Type myAbstractType;
  private final Type myType;
  private final DomInvocationHandler myParent;
  private final DomManagerImpl myManager;
  private final EvaluatedXmlName myTagName;
  private final Converter myGenericConverter;
  private XmlTag myXmlTag;

  private XmlFile myFile;
  private DomFileElementImpl myRoot;
  private final DomElement myProxy;
  private final Set<EvaluatedXmlName> myInitializedChildren = new THashSet<EvaluatedXmlName>();
  private final Map<Pair<EvaluatedXmlName, Integer>, IndexedElementInvocationHandler> myFixedChildren =
    new THashMap<Pair<EvaluatedXmlName, Integer>, IndexedElementInvocationHandler>();
  private final Map<EvaluatedXmlName, AttributeChildInvocationHandler> myAttributeChildren = new THashMap<EvaluatedXmlName, AttributeChildInvocationHandler>();
  final private GenericInfoImpl myGenericInfo;
  private final Map<EvaluatedXmlName, Class> myFixedChildrenClasses = new THashMap<EvaluatedXmlName, Class>();
  private Throwable myInvalidated;
  private InvocationCache myInvocationCache;
  private final Factory<Converter> myGenericConverterFactory = new Factory<Converter>() {
    public Converter create() {
      return myGenericConverter;
    }
  };
  private final ConcurrentFactoryMap<JavaMethod, Converter> myScalarConverters = new ConcurrentFactoryMap<JavaMethod, Converter>() {
    protected Converter create(final JavaMethod method) {
      final Type returnType = method.getGenericReturnType();
      final Type type = returnType == void.class ? method.getGenericParameterTypes()[0] : returnType;
      final Class parameter = ReflectionUtil.substituteGenericType(type, myType);
      LOG.assertTrue(parameter != null, type + " " + myType);
      final Converter converter = getConverter(method, parameter, type instanceof TypeVariable ? myGenericConverterFactory : Factory.NULL_FACTORY);
      LOG.assertTrue(converter != null, "No converter specified: String<->" + parameter.getName());
      return converter;
    }
  };

  private static final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
  private static final Lock r = rwl.readLock();
  private static final Lock w = rwl.writeLock();

  protected DomInvocationHandler(final Type type,
                                 final XmlTag tag,
                                 final DomInvocationHandler parent,
                                 final EvaluatedXmlName tagName,
                                 final DomManagerImpl manager) {
    myManager = manager;
    myParent = parent;
    myTagName = tagName;
    myAbstractType = type;

    final Type concreteInterface = manager.getTypeChooserManager().getTypeChooser(type).chooseType(tag);
    myGenericInfo = manager.getGenericInfo(concreteInterface);
    myType = concreteInterface;

    final Converter converter = getConverter(this, DomUtil.getGenericValueParameter(concreteInterface), Factory.NULL_FACTORY);
    myGenericConverter = converter;
    myInvocationCache =
      manager.getInvocationCache(new Pair<Type, Type>(concreteInterface, converter == null ? null : converter.getClass()));
    final Class<?> rawType = getRawType();
    Class<? extends DomElement> implementation = manager.getImplementation(rawType);
    final boolean isInterface = ReflectionCache.isInterface(rawType);
    if (implementation == null && !isInterface) {
      implementation = (Class<? extends DomElement>)rawType;
    }
    myProxy = AdvancedProxy.createProxy(this, implementation, isInterface ? new Class[]{rawType} : ArrayUtil.EMPTY_CLASS_ARRAY);
    attach(tag);
  }

  @NotNull
  public <T extends DomElement> DomFileElementImpl<T> getRoot() {
    LOG.assertTrue(isValid());
    if (myRoot == null) {
      myRoot = myParent.getRoot();
    }
    return myRoot;
  }

  @Nullable
  public DomElement getParent() {
    LOG.assertTrue(isValid());
    return myParent.getProxy();
  }

  @Nullable
  final DomInvocationHandler getParentHandler() {
    return myParent;
  }

  public final Type getDomElementType() {
    return myType;
  }

  final Type getAbstractType() {
    return myAbstractType;
  }

  @Nullable
  protected String getValue() {
    final XmlTag tag = getXmlTag();
    return tag == null ? null : getTagValue(tag);
  }

  protected void setValue(@Nullable final String value) {
    final XmlTag tag = ensureTagExists();
    myManager.runChange(new Runnable() {
      public void run() {
        setTagValue(tag, value);
      }
    });
    myManager.fireEvent(new TagValueChangeEvent(getProxy(), value));
  }

  public final void copyFrom(DomElement other) {
    if (other == getProxy()) return;
    assert other.getDomElementType().equals(myType);

    if (other.getXmlElement() == null) {
      undefine();
      return;
    }

    ensureXmlElementExists();
    for (final AttributeChildDescriptionImpl description : myGenericInfo.getAttributeChildrenDescriptions()) {
      description.getDomAttributeValue(this).setStringValue(description.getDomAttributeValue(other).getStringValue());
    }
    for (final FixedChildDescriptionImpl description : myGenericInfo.getFixedChildrenDescriptions()) {
      final List<? extends DomElement> list = description.getValues(getProxy());
      final List<? extends DomElement> otherValues = description.getValues(other);
      for (int i = 0; i < list.size(); i++) {
        final DomElement otherValue = otherValues.get(i);
        final DomElement value = list.get(i);
        if (otherValue.getXmlElement() == null) {
          value.undefine();
        } else {
          value.copyFrom(otherValue);
        }
      }
    }
    for (final CollectionChildDescriptionImpl description : myGenericInfo.getCollectionChildrenDescriptions()) {
      for (final DomElement value : description.getValues(getProxy())) {
        value.undefine();
      }
      for (final DomElement otherValue : description.getValues(other)) {
        description.addValue(getProxy(), otherValue.getDomElementType()).copyFrom(otherValue);
      }
    }

    final String stringValue = DomManagerImpl.getDomInvocationHandler(other).getValue();
    if (StringUtil.isNotEmpty(stringValue)) {
      setValue(stringValue);
    }
  }


  public final <T extends DomElement> T createMockCopy(final boolean physical) {
    final T copy = myManager.createMockElement((Class<? extends T>)getRawType(), getProxy().getModule(), physical);
    copy.copyFrom(getProxy());
    return copy;
  }

  @NotNull
  public String getXmlElementNamespace() {
    final XmlElement element = getParentHandler().getXmlElement();
    assert element != null;
    return getXmlName().getNamespace(element);
  }

  @Nullable
  public String getXmlElementNamespaceKey() {
    return getXmlName().getXmlName().getNamespaceKey();
  }

  public final Module getModule() {
    final Module module = ModuleUtil.findModuleForPsiElement(getFile());
    return module != null ? module : getRoot().getUserData(DomManagerImpl.MOCK_ELEMENT_MODULE);
  }

  public XmlTag ensureTagExists() {
    if (myXmlTag != null) return myXmlTag;

    attach(setEmptyXmlTag());

    myManager.fireEvent(new ElementDefinedEvent(getProxy()));
    addRequiredChildren();
    return myXmlTag;
  }

  public XmlElement getXmlElement() {
    return getXmlTag();
  }

  public XmlElement ensureXmlElementExists() {
    return ensureTagExists();
  }

  protected final XmlTag createChildTag(final EvaluatedXmlName tagName) {
    final String localName = tagName.getLocalName();
    if (localName.contains(":")) {
      try {
        return myXmlTag.getManager().getElementFactory().createTagFromText("<" + localName + "/>");
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    final XmlElement element = getXmlElement();
    assert element != null;
    return myXmlTag.createChildTag(localName, tagName.getNamespace(element), "", false);
  }

  public boolean isValid() {
    return myInvalidated == null;
  }

  @NotNull
  public final GenericInfoImpl getGenericInfo() {
    myGenericInfo.buildMethodMaps();
    return myGenericInfo;
  }

  protected abstract void undefineInternal();

  public final void undefine() {
    undefineInternal();
  }

  protected final void detachChildren() {
    for (final AttributeChildInvocationHandler handler : myAttributeChildren.values()) {
      handler.detach(false);
    }
    for (final IndexedElementInvocationHandler handler : myFixedChildren.values()) {
      handler.detach(false);
    }
  }

  protected final void deleteTag(final XmlTag tag) {
    final boolean changing = myManager.setChanging(true);
    try {
      tag.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      myManager.setChanging(changing);
    }
    setXmlTagToNull();
  }

  protected final void setXmlTagToNull() {
    myXmlTag = null;
  }

  protected final void fireUndefinedEvent() {
    myManager.fireEvent(new ElementUndefinedEvent(getProxy()));
  }

  protected abstract XmlTag setEmptyXmlTag();

  protected void addRequiredChildren() {
    for (final DomChildrenDescription description : myGenericInfo.getChildrenDescriptions()) {
      if (description instanceof DomAttributeChildDescription) {
        final Required required = description.getAnnotation(Required.class);

        if (required != null && required.value()) {
          description.getValues(getProxy()).get(0).ensureXmlElementExists();
        }
      }
      else if (description instanceof DomFixedChildDescription) {
        final DomFixedChildDescription childDescription = (DomFixedChildDescription)description;
        List<? extends DomElement> values = null;
        final int count = childDescription.getCount();
        for (int i = 0; i < count; i++) {
          final Required required = childDescription.getAnnotation(i, Required.class);
          if (required != null && required.value()) {
            if (values == null) {
              values = description.getValues(getProxy());
            }
            values.get(i).ensureTagExists();
          }
        }
      }
    }
  }

  @NotNull
  public final String getXmlElementName() {
    return myTagName.getLocalName();
  }

  @NotNull
  public final EvaluatedXmlName getXmlName() {
    return myTagName;
  }

  public void accept(final DomElementVisitor visitor) {
    ProgressManager.getInstance().checkCanceled();
    myManager.getVisitorDescription(visitor.getClass()).acceptElement(visitor, getProxy());
  }

  public void acceptChildren(DomElementVisitor visitor) {
    final DomElement element = getProxy();
    for (final DomChildrenDescription description : getGenericInfo().getChildrenDescriptions()) {
      for (final DomElement value : description.getValues(element)) {
        value.accept(visitor);
      }
    }
  }

  public final void initializeAllChildren() {
    myGenericInfo.buildMethodMaps();
    for (final XmlName s : myGenericInfo.getFixedChildrenNames()) {
      checkInitialized(s.createEvaluatedXmlName(this));
    }
    for (final XmlName s : myGenericInfo.getCollectionChildrenNames()) {
      checkInitialized(s.createEvaluatedXmlName(this));
    }
    for (final XmlName s : myGenericInfo.getAttributeChildrenNames()) {
      checkInitialized(s.createEvaluatedXmlName(this));
    }
  }

  final List<CollectionElementInvocationHandler> getCollectionChildren() {
    final List<CollectionElementInvocationHandler> collectionChildren = new ArrayList<CollectionElementInvocationHandler>();
    final XmlTag tag = getXmlTag();
    if (tag != null) {
      for (XmlTag xmlTag : tag.getSubTags()) {
        final DomInvocationHandler cachedElement = DomManagerImpl.getCachedElement(xmlTag);
        if (cachedElement instanceof CollectionElementInvocationHandler) {
          collectionChildren.add((CollectionElementInvocationHandler)cachedElement);
        }
      }
    }
    return collectionChildren;
  }

  @NotNull
  protected final synchronized Converter getScalarConverter(final JavaMethod method) {
    return myScalarConverters.get(method);
  }

  @Nullable
  protected abstract AnnotatedElement getChildDescription();

  @Nullable
  public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
    final AnnotatedElement childDescription = getChildDescription();
    if (childDescription != null) {
      final T annotation = childDescription.getAnnotation(annotationClass);
      if (annotation != null) return annotation;
    }

    return getRawType().getAnnotation(annotationClass);
  }

  @Nullable
  private Converter getConverter(final AnnotatedElement annotationProvider,
                                 Class parameter,
                                 final Factory<Converter> continuation) {
    final Resolve resolveAnnotation = annotationProvider.getAnnotation(Resolve.class);
    if (resolveAnnotation != null) {
      final Class<? extends DomElement> aClass = resolveAnnotation.value();
      if (!DomElement.class.equals(aClass)) {
        return DomResolveConverter.createConverter(aClass);
      } else {
        LOG.assertTrue(parameter != null, "You should specify @Resolve#value() parameter");
        return DomResolveConverter.createConverter(parameter);
      }
    }

    final Convert convertAnnotation = annotationProvider.getAnnotation(Convert.class);
    final ConverterManager converterManager = myManager.getConverterManager();
    if (convertAnnotation != null) {
      return converterManager.getConverterInstance(convertAnnotation.value());
    }

    final Converter converter = continuation.create();
    if (converter != null) {
      return converter;
    }

    return parameter == null ? null : converterManager.getConverterByClass(parameter);
  }

  @NotNull
  public final DomElement getProxy() {
    return myProxy;
  }

  @NotNull
  protected final XmlFile getFile() {
    if (myFile == null) {
      myFile = getRoot().getFile();
    }
    return myFile;
  }

  public final DomNameStrategy getNameStrategy() {
    final Class<?> rawType = getRawType();
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(rawType, isAttribute());
    if (strategy != null) {
      return strategy;
    }
    final DomInvocationHandler parent = getParentHandler();
    return parent != null ? parent.getNameStrategy() : DomNameStrategy.HYPHEN_STRATEGY;
  }

  protected boolean isAttribute() {
    return false;
  }

  @NotNull
  public ElementPresentation getPresentation() {
    return new ElementPresentation() {
      public String getElementName() {
        return ElementPresentationManager.getElementName(getProxy());
      }

      public String getTypeName() {
        return ElementPresentationManager.getTypeNameForObject(getProxy());
      }

      public Icon getIcon() {
        return ElementPresentationManager.getIcon(getProxy());
      }
    };
  }

  public final GlobalSearchScope getResolveScope() {
    return getRoot().getResolveScope();
  }

  private static <T extends DomElement> T _getParentOfType(Class<T> requiredClass, DomElement element) {
    while (element != null && !(requiredClass.isInstance(element))) {
      element = element.getParent();
    }
    return (T)element;
  }

  public final <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict) {
    return _getParentOfType(requiredClass, strict ? getParent() : getProxy());
  }

  private Invocation createInvocation(final JavaMethod method) {
    if (DomImplUtil.isTagValueGetter(method)) {
      return createGetValueInvocation(getScalarConverter(method));
    }

    if (DomImplUtil.isTagValueSetter(method)) {
      return createSetValueInvocation(getScalarConverter(method));
    }

    return myGenericInfo.createInvocation(method);
  }

  private Invocation createSetValueInvocation(final Converter converter) {
    return new SetInvocation(converter);
  }

  private Invocation createGetValueInvocation(final Converter converter) {
    return new GetInvocation(converter);
  }

  @NotNull
  final IndexedElementInvocationHandler getFixedChild(final Pair<EvaluatedXmlName, Integer> info) {
    r.lock();
    try {
      final IndexedElementInvocationHandler handler = myFixedChildren.get(info);
      if (handler == null) {
        LOG.assertTrue(false, this + " " + info.toString());
      }
      return handler;
    }
    finally {
      r.unlock();
    }
  }

  final Collection<IndexedElementInvocationHandler> getFixedChildren() {
    return myFixedChildren.values();
  }

  @NotNull
  final AttributeChildInvocationHandler getAttributeChild(final JavaMethodSignature method) {
    myGenericInfo.buildMethodMaps();
    return getAttributeChild(myGenericInfo.getAttributeName(method).createEvaluatedXmlName(this));
  }

  @NotNull
  final AttributeChildInvocationHandler getAttributeChild(final EvaluatedXmlName attributeName) {
    checkInitialized(ATTRIBUTES);
    final AttributeChildInvocationHandler domElement = myAttributeChildren.get(attributeName);
    assert domElement != null : attributeName;
    return domElement;
  }

  @Nullable
  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      return doInvoke(JavaMethodSignature.getSignature(method), args);
    }
    catch (InvocationTargetException ex) {
      throw ex.getTargetException();
    }
  }

  @Nullable
  private Object doInvoke(final JavaMethodSignature signature, final Object... args) throws Throwable {
    Invocation invocation = myInvocationCache.getInvocation(signature);
    if (invocation == null) {
      invocation = createInvocation(JavaMethod.getMethod(getRawType(), signature));
      myInvocationCache.putInvocation(signature, invocation);
    }
    return invocation.invoke(this, args);
  }

  static void setTagValue(final XmlTag tag, final String value) {
    tag.getValue().setText(value);
  }

  static String getTagValue(final XmlTag tag) {
    return tag.getValue().getTrimmedText();
  }

  public final String toString() {
    if (ReflectionCache.isAssignable(GenericValue.class, getRawType())) {
      return ((GenericValue)getProxy()).getStringValue();
    }
    return myType.toString() + " @" + hashCode();
  }

  final void checkInitialized(final EvaluatedXmlName qname) {
    if (!isValid()) {
      throw new RuntimeException("element " + myType.toString() + " is not valid", myInvalidated);
    }
    checkParentInitialized();
    r.lock();
    try {
      if (myInitializedChildren.contains(qname)) {
        return;
      }
    }
    finally {
      r.unlock();
    }
    w.lock();
    try {
      if (myInitializedChildren.contains(qname)) {
        return;
      }
      myGenericInfo.buildMethodMaps();

      if (ATTRIBUTES == qname) {
        for (Map.Entry<XmlName, JavaMethodSignature> entry : myGenericInfo.getAttributeChildrenEntries()) {
          getOrCreateAttributeChild(entry.getValue().findMethod(getRawType()), entry.getKey().createEvaluatedXmlName(this));
        }
      }

      final XmlTag tag = getXmlTag();
      if (myGenericInfo.isFixedChild(qname.getXmlName())) {
        final int count = myGenericInfo.getFixedChildrenCount(qname.getXmlName());
        for (int i = 0; i < count; i++) {
          getOrCreateIndexedChild(findSubTag(tag, qname, i), Pair.create(qname, i));
        }
      }
      else if (tag != null && myGenericInfo.isCollectionChild(qname.getXmlName())) {
        for (XmlTag subTag : DomImplUtil.findSubTags(tag, qname, this)) {
          new CollectionElementInvocationHandler(myGenericInfo.getCollectionChildrenType(qname.getXmlName()), qname, subTag, this);
        }
      }
      myInitializedChildren.add(qname);
    }
    finally {
      w.unlock();
    }
  }

  private void checkParentInitialized() {
    if (myXmlTag == null && myParent != null && !isAnythingInitialized() && myParent.isValid()) {
      myParent.checkInitialized(myTagName);
    }
  }

  private void getOrCreateAttributeChild(final Method method, final EvaluatedXmlName attributeName) {
    final AttributeChildInvocationHandler handler =
      new AttributeChildInvocationHandler(JavaMethod.getMethod(getRawType(), method).getGenericReturnType(), getXmlTag(), this, attributeName, myManager);
    myAttributeChildren.put(handler.getXmlName(), handler);
  }

  private void getOrCreateIndexedChild(final XmlTag subTag, final Pair<EvaluatedXmlName, Integer> pair) {
    IndexedElementInvocationHandler handler = myFixedChildren.get(pair);
    if (handler == null) {
      handler = createIndexedChild(subTag, pair);
      myFixedChildren.put(pair, handler);
    }
    else {
      handler.attach(subTag);
    }
  }

  private IndexedElementInvocationHandler createIndexedChild(final XmlTag subTag, final Pair<EvaluatedXmlName, Integer> pair) {
    final EvaluatedXmlName qname = pair.getFirst();
    final Type type = getIndexedChildType(qname, pair);
    return new IndexedElementInvocationHandler(type, subTag, this, qname, pair.getSecond());
  }

  private Type getIndexedChildType(final EvaluatedXmlName qname, final Pair<EvaluatedXmlName, Integer> pair) {
    final Type type;
    if (myFixedChildrenClasses.containsKey(qname)) {
      type = getFixedChildrenClass(qname);
    }
    else {
      final JavaMethodSignature signature = myGenericInfo.getFixedChildGetter(Pair.create(qname.getXmlName(), pair.second));
      final JavaMethod method = JavaMethod.getMethod(getRawType(), signature);
      assert method != null;
      type = method.getGenericReturnType();
    }
    return type;
  }

  protected final Class<?> getRawType() {
    return ReflectionUtil.getRawType(myType);
  }

  protected final Class getFixedChildrenClass(final EvaluatedXmlName tagName) {
    return myFixedChildrenClasses.get(tagName);
  }

  @Nullable
  private XmlTag findSubTag(final XmlTag tag, final EvaluatedXmlName qname, final int index) {
    if (tag == null) return null;
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, qname, this);
    return subTags.size() <= index ? null : subTags.get(index);
  }

  @Nullable
  public XmlTag getXmlTag() {
    checkParentInitialized();
    return myXmlTag;
  }

  protected final void detach(boolean invalidate) {
    w.lock();
    try {
      if (invalidate && myInvalidated == null) {
        myInvalidated = new Throwable();
      }
      if (!myInitializedChildren.isEmpty()) {
        for (DomInvocationHandler handler : myFixedChildren.values()) {
          handler.detach(invalidate);
        }
        if (myXmlTag != null && myXmlTag.isValid()) {
          for (CollectionElementInvocationHandler handler : getCollectionChildren()) {
            handler.detach(true);
          }
        }
      }

      myInitializedChildren.clear();
      removeFromCache();
      setXmlTagToNull();
    } finally {
      w.unlock();
    }
  }

  protected void removeFromCache() {
    DomManagerImpl.setCachedElement(myXmlTag, null);
  }

  protected final void attach(final XmlTag tag) {
    w.lock();
    try {
      myXmlTag = tag;
      cacheInTag(tag);
    } finally {
      w.unlock();
    }
  }

  protected void cacheInTag(final XmlTag tag) {
    DomManagerImpl.setCachedElement(tag, this);
  }

  @NotNull
  public final DomManagerImpl getManager() {
    return myManager;
  }

  boolean isIndicator() {
    return false;
  }

  public final DomElement addChild(final EvaluatedXmlName tagName, final Type type, int index) throws IncorrectOperationException {
    checkInitialized(tagName);
    final XmlTag tag = addEmptyTag(tagName, index);
    final CollectionElementInvocationHandler handler = new CollectionElementInvocationHandler(type, tagName, tag, this);
    final DomElement element = handler.getProxy();
    myManager.fireEvent(new CollectionElementAddedEvent(element, tag.getName()));
    handler.addRequiredChildren();
    return element;
  }

  protected final void createFixedChildrenTags(EvaluatedXmlName tagName, int count) {
    checkInitialized(tagName);
    final XmlTag tag = ensureTagExists();
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, tagName, this);
    if (subTags.size() < count) {
      getFixedChild(Pair.create(tagName, count - 1)).ensureTagExists();
    }
  }

  private XmlTag addEmptyTag(final EvaluatedXmlName tagName, int index) throws IncorrectOperationException {
    final XmlTag tag = ensureTagExists();
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, tagName, this);
    if (subTags.size() < index) {
      index = subTags.size();
    }
    final boolean changing = myManager.setChanging(true);
    try {
      XmlTag newTag = createChildTag(tagName);
      if (index == 0) {
        if (subTags.isEmpty()) {
          return (XmlTag)tag.add(newTag);
        }

        return (XmlTag)tag.addBefore(newTag, subTags.get(0));
      }

      return (XmlTag)tag.addAfter(newTag, subTags.get(index - 1));
    }
    finally {
      myManager.setChanging(changing);
    }
  }

  public final boolean isInitialized(final EvaluatedXmlName qname) {
    r.lock();
    try {
      return myInitializedChildren.contains(qname);
    } finally{
      r.unlock();
    }
  }

  public final boolean isAnythingInitialized() {
    r.lock();
    try {
      return !myInitializedChildren.isEmpty();
    } finally{
      r.unlock();
    }
  }

  public final boolean areAttributesInitialized() {
    return isInitialized(ATTRIBUTES);
  }

  public void setFixedChildClass(final EvaluatedXmlName tagName, final Class<? extends DomElement> aClass) {
    assert !isInitialized(tagName);
    myFixedChildrenClasses.put(tagName, aClass);
  }
}

