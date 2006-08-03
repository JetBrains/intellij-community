/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.CollectionElementAddedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * @author peter
 */
public abstract class DomInvocationHandler implements InvocationHandler, DomElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomInvocationHandler");
  private static final String ATTRIBUTES = "@";
  public static Method ACCEPT_METHOD = null;
  public static Method ACCEPT_CHILDREN_METHOD = null;
  protected static Method CREATE_STABLE_COPY_METHOD = null;

  static {
    try {
      ACCEPT_METHOD = DomElement.class.getMethod("accept", DomElementVisitor.class);
      ACCEPT_CHILDREN_METHOD = DomElement.class.getMethod("acceptChildren", DomElementVisitor.class);
      CREATE_STABLE_COPY_METHOD = DomElement.class.getMethod("createStableCopy");
    }
    catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  private final Type myAbstractType;
  private final Type myType;
  private final DomInvocationHandler myParent;
  private final DomManagerImpl myManager;
  private final String myTagName;
  private final Converter myGenericConverter;
  private XmlTag myXmlTag;

  private XmlFile myFile;
  private final DomElement myProxy;
  private final Set<String> myInitializedChildren = new THashSet<String>();
  private final Map<Pair<String, Integer>, IndexedElementInvocationHandler> myFixedChildren =
    new THashMap<Pair<String, Integer>, IndexedElementInvocationHandler>();
  private final Map<String, AttributeChildInvocationHandler> myAttributeChildren = new THashMap<String, AttributeChildInvocationHandler>();
  final private GenericInfoImpl myGenericInfo;
  private final Map<String, Class> myFixedChildrenClasses = new THashMap<String, Class>();
  private boolean myInvalidated;
  private InvocationCache myInvocationCache;
  private final Factory<Converter> myGenericConverterFactory = new Factory<Converter>() {
    public Converter create() {
      return myGenericConverter;
    }
  };
  private final FactoryMap<JavaMethod, Converter> myScalarConverters = new FactoryMap<JavaMethod, Converter>() {
    protected Converter create(final JavaMethod method) {
      final Type returnType = method.getGenericReturnType();
      final Type type = returnType == void.class ? method.getGenericParameterTypes()[0] : returnType;
      final Class parameter = DomReflectionUtil.substituteGenericType(type, myType);
      assert parameter != null : type + " " + myType;
      final Converter converter = getConverter(method, parameter, type instanceof TypeVariable ? myGenericConverterFactory : Factory.NULL_FACTORY);
      assert converter != null : "No converter specified: String<->" + parameter.getName();
      return converter;
    }
  };

  protected DomInvocationHandler(final Type type,
                                 final XmlTag tag,
                                 final DomInvocationHandler parent,
                                 final String tagName,
                                 final DomManagerImpl manager) {
    myManager = manager;
    myParent = parent;
    myTagName = tagName;
    myAbstractType = type;

    final Type concreteInterface = manager.getTypeChooserManager().getTypeChooser(type).chooseType(tag);
    final Converter converter = getConverter(this, DomUtil.getGenericValueParameter(concreteInterface), Factory.NULL_FACTORY);

    myGenericInfo = manager.getGenericInfo(concreteInterface);
    myType = concreteInterface;
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

  @Nullable
  public <T extends DomElement> DomFileElementImpl<T> getRoot() {
    return isValid() ? (DomFileElementImpl<T>)myParent.getRoot() : null;
  }

  @Nullable
  public DomElement getParent() {
    return isValid() ? myParent.getProxy() : null;
  }

  final DomInvocationHandler getParentHandler() {
    return myParent;
  }

  public final Type getDomElementType() {
    return myType;
  }

  final Type getAbstractType() {
    return myAbstractType;
  }

  public final void copyFrom(DomElement other) {
    if (other == getProxy()) return;
    assert other.getDomElementType().equals(myType);
    final XmlTag fromTag = other.getXmlTag();
    if (fromTag == null) {
      if (getXmlTag() != null) {
        undefine();
      }
      return;
    }

    final XmlTag tag = ensureTagExists();
    detach(false);
    synchronized (PsiLock.LOCK) {
      myManager.runChange(new Runnable() {
        public void run() {
          try {
            copyTags(fromTag, tag);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });
    }
    attach(tag);
  }

  private void copyTags(final XmlTag fromTag, final XmlTag toTag) throws IncorrectOperationException {
    for (PsiElement child : toTag.getChildren()) {
      if (child instanceof XmlAttribute || child instanceof XmlTag) {
        child.delete();
      }
    }

    PsiElement child = fromTag.getFirstChild();
    boolean hasChildren = false;
    while (child != null) {
      if (child instanceof XmlTag) {
        final XmlTag xmlTag = (XmlTag)child;
        copyTags(xmlTag, (XmlTag)toTag.add(createEmptyTag(xmlTag.getName())));
        hasChildren = true;
      }
      else if (child instanceof XmlAttribute) {
        toTag.add(child);
      }
      child = child.getNextSibling();
    }
    if (!hasChildren) {
      toTag.getValue().setText(fromTag.getValue().getText());
    }
  }

  public final <T extends DomElement> T createMockCopy(final boolean physical) {
    final T copy = myManager.createMockElement((Class<? extends T>)getRawType(), getModule(), physical);
    copy.copyFrom(getProxy());
    return copy;
  }

  public final Module getModule() {
    final Module module = ModuleUtil.findModuleForPsiElement(getFile());
    return module != null ? module : getRoot().getUserData(DomManagerImpl.MODULE);
  }

  public XmlTag ensureTagExists() {
    if (myXmlTag != null) return myXmlTag;

    try {
      attach(setXmlTag(createEmptyTag()));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    myManager.fireEvent(new ElementDefinedEvent(getProxy()), true);
    addRequiredChildren();
    return myXmlTag;
  }

  public XmlElement getXmlElement() {
    return getXmlTag();
  }

  public XmlElement ensureXmlElementExists() {
    return ensureTagExists();
  }

  protected final XmlTag createEmptyTag() throws IncorrectOperationException {
    return createEmptyTag(myTagName);
  }

  protected final XmlTag createEmptyTag(final String tagName) throws IncorrectOperationException {
    return getFile().getManager().getElementFactory().createTagFromText("<" + tagName + "/>");
  }

  public boolean isValid() {
    if (!myInvalidated && (myXmlTag != null && !myXmlTag.isValid() || myParent != null && !myParent.isValid())) {
      myInvalidated = true;
      return false;
    }
    return !myInvalidated;
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
    myManager.fireEvent(new ElementUndefinedEvent(getProxy()), true);
  }

  protected abstract XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException;

  protected final void addRequiredChildren() {
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
    return myTagName;
  }

  protected final DomElement findCallerProxy(Method method) {
    final Object o = InvocationStack.INSTANCE.findDeepestInvocation(method, new Condition<Object>() {
      public boolean value(final Object object) {
        return ModelMergerUtil.getImplementation(object, DomElement.class) == null;
      }
    });
    final DomElement element = ModelMergerUtil.getImplementation(o, DomElement.class);
    return element == null ? getProxy() : element;
  }

  public void accept(final DomElementVisitor visitor) {
    myManager.getVisitorDescription(visitor.getClass()).acceptElement(visitor, findCallerProxy(ACCEPT_METHOD));
  }

  public void acceptChildren(DomElementVisitor visitor) {
    final DomElement element = ModelMergerUtil.getImplementation(findCallerProxy(ACCEPT_CHILDREN_METHOD), DomElement.class);
    for (final DomChildrenDescription description : getGenericInfo().getChildrenDescriptions()) {
      for (final DomElement value : description.getValues(element)) {
        value.accept(visitor);
      }
    }
  }

  public final void initializeAllChildren() {
    myGenericInfo.buildMethodMaps();
    for (final String s : myGenericInfo.getFixedChildrenNames()) {
      checkInitialized(s);
    }
    for (final String s : myGenericInfo.getCollectionChildrenNames()) {
      checkInitialized(s);
    }
    for (final String s : myGenericInfo.getAttributeChildrenNames()) {
      checkInitialized(s);
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
  protected final Converter getScalarConverter(final JavaMethod method) {
    return myScalarConverters.get(method);
  }

  @Nullable
  private Converter getConverter(final AnnotatedElement annotationProvider,
                                 Class parameter,
                                 final Factory<Converter> continuation) {
    final Resolve resolveAnnotation = (Resolve)annotationProvider.getAnnotation(Resolve.class);
    if (resolveAnnotation != null) {
      return DomResolveConverter.createConverter(resolveAnnotation.value());
    }

    final Convert convertAnnotation = (Convert)annotationProvider.getAnnotation(Convert.class);
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

  public final DomElement getProxy() {
    return myProxy;
  }

  @NotNull
  protected final XmlFile getFile() {
    assert isValid();
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

  protected final Invocation createInvocation(final JavaMethod method) throws IllegalAccessException, InstantiationException {
    if (DomImplUtil.isTagValueGetter(method)) {
      return createGetValueInvocation(getScalarConverter(method));
    }

    if (DomImplUtil.isTagValueSetter(method)) {
      return createSetValueInvocation(getScalarConverter(method));
    }

    return myGenericInfo.createInvocation(method);
  }

  protected Invocation createSetValueInvocation(final Converter converter) {
    return new SetValueInvocation(converter);
  }

  protected Invocation createGetValueInvocation(final Converter converter) {
    return new GetValueInvocation(converter);
  }

  @NotNull
  final IndexedElementInvocationHandler getFixedChild(final Pair<String, Integer> info) {
    return myFixedChildren.get(info);
  }

  final Collection<IndexedElementInvocationHandler> getFixedChildren() {
    return myFixedChildren.values();
  }

  @NotNull
  final AttributeChildInvocationHandler getAttributeChild(final JavaMethodSignature method) {
    myGenericInfo.buildMethodMaps();
    final String attributeName = myGenericInfo.getAttributeName(method);
    assert attributeName != null : method.toString();
    return getAttributeChild(attributeName);
  }

  @NotNull
  final AttributeChildInvocationHandler getAttributeChild(final String attributeName) {
    checkInitialized(ATTRIBUTES);
    final AttributeChildInvocationHandler domElement = myAttributeChildren.get(attributeName);
    assert domElement != null : attributeName;
    return domElement;
  }

  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      InvocationStack.INSTANCE.push(method, null);
      return doInvoke(JavaMethodSignature.getSignature(method), args);
    }
    catch (InvocationTargetException ex) {
      throw ex.getTargetException();
    }
    finally {
      InvocationStack.INSTANCE.pop();
    }
  }

  public final Object doInvoke(final JavaMethodSignature signature, final Object... args) throws Throwable {
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
/*
    final XmlText[] textElements = tag.getValue().getTextElements();
    return textElements.length != 0 ? textElements[0].getValue().trim() : "";*/
  }

  public final String toString() {
    return myType.toString() + " @" + hashCode();
  }

  final void checkInitialized(final String qname) {
    assert isValid();
    checkParentInitialized();
    synchronized (PsiLock.LOCK) {
      if (myInitializedChildren.contains(qname)) {
        return;
      }
      try {
        myGenericInfo.buildMethodMaps();

        if (ATTRIBUTES.equals(qname)) {
          for (Map.Entry<JavaMethodSignature, String> entry : myGenericInfo.getAttributeChildrenEntries()) {
            getOrCreateAttributeChild(entry.getKey().findMethod(getRawType()), entry.getValue());
          }
        }

        final XmlTag tag = getXmlTag();
        if (myGenericInfo.isFixedChild(qname)) {
          final int count = myGenericInfo.getFixedChildrenCount(qname);
          for (int i = 0; i < count; i++) {
            getOrCreateIndexedChild(findSubTag(tag, qname, i), new Pair<String, Integer>(qname, i));
          }
        }
        else if (tag != null && myGenericInfo.isCollectionChild(qname)) {
          for (XmlTag subTag : tag.findSubTags(qname)) {
            new CollectionElementInvocationHandler(myGenericInfo.getCollectionChildrenType(qname), subTag, this);
          }
        }

      }
      finally {
        myInitializedChildren.add(qname);
      }
    }
  }

  private void checkParentInitialized() {
    if (myXmlTag == null && myParent != null && myInitializedChildren.isEmpty() && myParent.isValid()) {
      myParent.checkInitialized(myTagName);
    }
  }

  private void getOrCreateAttributeChild(final Method method, final String attributeName) {
    final AttributeChildInvocationHandler handler =
      new AttributeChildInvocationHandler(JavaMethod.getMethod(getRawType(), method).getGenericReturnType(), getXmlTag(), this, attributeName, myManager);
    myAttributeChildren.put(handler.getXmlElementName(), handler);
  }

  private IndexedElementInvocationHandler getOrCreateIndexedChild(final XmlTag subTag, final Pair<String, Integer> pair) {
    IndexedElementInvocationHandler handler = myFixedChildren.get(pair);
    if (handler == null) {
      handler = createIndexedChild(subTag, pair);
      myFixedChildren.put(pair, handler);
    }
    else {
      handler.attach(subTag);
    }
    return handler;
  }

  private IndexedElementInvocationHandler createIndexedChild(final XmlTag subTag, final Pair<String, Integer> pair) {
    final String qname = pair.getFirst();
    final Type type = getIndexedChildType(qname, pair);
    return new IndexedElementInvocationHandler(type, subTag, this, qname, pair.getSecond());
  }

  private Type getIndexedChildType(final String qname, final Pair<String, Integer> pair) {
    final Type type;
    if (myFixedChildrenClasses.containsKey(qname)) {
      type = getFixedChildrenClass(qname);
    }
    else {
      final JavaMethodSignature signature = myGenericInfo.getFixedChildGetter(pair);
      final JavaMethod method = JavaMethod.getMethod(getRawType(), signature);
      assert method != null;
      type = method.getGenericReturnType();
    }
    return type;
  }

  protected final Class<?> getRawType() {
    return DomReflectionUtil.getRawType(myType);
  }

  protected final Class getFixedChildrenClass(final String tagName) {
    return myFixedChildrenClasses.get(tagName);
  }

  @Nullable
  protected static XmlTag findSubTag(final XmlTag tag, final String qname, final int index) {
    if (tag == null) {
      return null;
    }
    final XmlTag[] subTags = tag.findSubTags(qname);
    return subTags.length <= index ? null : subTags[index];
  }

  @Nullable
  public XmlTag getXmlTag() {
    checkParentInitialized();
    return myXmlTag;
  }

  protected final void detach(boolean invalidate) {
    synchronized (PsiLock.LOCK) {
      myInvalidated = invalidate;
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
    }
  }

  protected void removeFromCache() {
    DomManagerImpl.setCachedElement(myXmlTag, null);
  }

  protected final void attach(final XmlTag tag) {
    synchronized (PsiLock.LOCK) {
      myXmlTag = tag;
      cacheInTag(tag);
    }
  }

  protected void cacheInTag(final XmlTag tag) {
    DomManagerImpl.setCachedElement(tag, this);
  }

  public final DomManagerImpl getManager() {
    return myManager;
  }

  boolean isIndicator() {
    return false;
  }

  public final DomElement addChild(final String tagName, final Type type, int index) throws IncorrectOperationException {
    checkInitialized(tagName);
    final XmlTag tag = addEmptyTag(tagName, index);
    final CollectionElementInvocationHandler handler = new CollectionElementInvocationHandler(type, tag, this);
    final DomElement element = handler.getProxy();
    myManager.fireEvent(new CollectionElementAddedEvent(element, tag.getName()), true);
    handler.addRequiredChildren();
    return element;
  }

  protected final void createFixedChildrenTags(String tagName, int count) throws IncorrectOperationException {
    checkInitialized(tagName);
    final XmlTag tag = ensureTagExists();
    final XmlTag[] subTags = tag.findSubTags(tagName);
    if (subTags.length < count) {
      getFixedChild(new Pair<String, Integer>(tagName, count - 1)).ensureTagExists();
    }
  }

  private XmlTag addEmptyTag(final String tagName, int index) throws IncorrectOperationException {
    final XmlTag tag = ensureTagExists();
    final XmlTag[] subTags = tag.findSubTags(tagName);
    if (subTags.length < index) {
      index = subTags.length;
    }
    final boolean changing = myManager.setChanging(true);
    try {
      XmlTag newTag = createEmptyTag(tagName);
      if (index == 0) {
        if (subTags.length == 0) {
          return (XmlTag)tag.add(newTag);
        }

        return (XmlTag)tag.addBefore(newTag, subTags[0]);
      }

      return (XmlTag)tag.addAfter(newTag, subTags[index - 1]);
    }
    finally {
      myManager.setChanging(changing);
    }
  }

  public final boolean isInitialized(final String qname) {
    synchronized (PsiLock.LOCK) {
      return myInitializedChildren.contains(qname);
    }
  }

  public final boolean isAnythingInitialized() {
    synchronized (PsiLock.LOCK) {
      return !myInitializedChildren.isEmpty();
    }
  }

  public final boolean areAttributesInitialized() {
    return isInitialized(ATTRIBUTES);
  }

  public void setFixedChildClass(final String tagName, final Class<? extends DomElement> aClass) {
    synchronized (PsiLock.LOCK) {
      assert !myInitializedChildren.contains(tagName);
      myFixedChildrenClasses.put(tagName, aClass);
    }
  }
}

