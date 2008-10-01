/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.*;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.ElementChangedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public abstract class DomInvocationHandler<T extends AbstractDomChildDescriptionImpl> extends UserDataHolderBase implements InvocationHandler, DomElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomInvocationHandler");
  public static final Method ACCEPT_METHOD = ReflectionUtil.getMethod(DomElement.class, "accept", DomElementVisitor.class);
  public static final Method ACCEPT_CHILDREN_METHOD = ReflectionUtil.getMethod(DomElement.class, "acceptChildren", DomElementVisitor.class);

  private final Type myAbstractType;
  private final Type myType;
  private final DomManagerImpl myManager;
  private final EvaluatedXmlName myTagName;
  private final T myChildDescription;
  private final Converter myGenericConverter;
  private DomParentStrategy myParentStrategy;

  private XmlFile myFile;
  private DomFileElementImpl myRoot;
  private final DomElement myProxy;
  private DomGenericInfoEx myGenericInfo;
  private final InvocationCache myInvocationCache;
  private final FactoryMap<JavaMethod, Converter> myScalarConverters = new FactoryMap<JavaMethod, Converter>() {
    protected Converter create(final JavaMethod method) {
      final Type returnType = method.getGenericReturnType();
      final Type type = returnType == void.class ? method.getGenericParameterTypes()[0] : returnType;
      final Class parameter = ReflectionUtil.substituteGenericType(type, myType);
      LOG.assertTrue(parameter != null, type + " " + myType);
      final Converter converter = getConverter(method, parameter, type instanceof TypeVariable ? myGenericConverter : null);
      LOG.assertTrue(converter != null, "No converter specified: String<->" + parameter.getName());
      return converter;
    }
  };

  protected DomInvocationHandler(Type type, DomParentStrategy parentStrategy,
                                 final EvaluatedXmlName tagName,
                                 final T childDescription,
                                 final DomManagerImpl manager,
                                 boolean dynamic) {
    myManager = manager;
    myParentStrategy = parentStrategy;
    myTagName = tagName;
    myChildDescription = childDescription;
    myAbstractType = type;
    myType = narrowType(type);

    final Converter converter = getConverter(this, DomUtil.getGenericValueParameter(myType), null);
    myGenericConverter = converter;
    myInvocationCache =
        manager.getInvocationCache(new Pair<Type, Type>(myType, converter == null ? null : converter.getClass()));
    final Class<?> rawType = getRawType();
    Class<? extends DomElement> implementation = manager.getImplementation(rawType);
    final boolean isInterface = ReflectionCache.isInterface(rawType);
    if (implementation == null && !isInterface) {
      implementation = (Class<? extends DomElement>)rawType;
    }
    myProxy = AdvancedProxy.createProxy(this, implementation, isInterface ? new Class[]{rawType} : ArrayUtil.EMPTY_CLASS_ARRAY);
    refreshGenericInfo(dynamic);
  }

  protected Type narrowType(@NotNull Type nominalType) {
    return nominalType;
  }

  @NotNull
  public <T extends DomElement> DomFileElementImpl<T> getRoot() {
    checkIsValid();
    return _getRoot();
  }

  protected <T extends DomElement> DomFileElementImpl<T> _getRoot() {
    if (myRoot == null) {
      final DomInvocationHandler parent = getParentHandler();
      assert parent != null : "getRoot() operation should be performed on the DOM having a parent, your DOM may be not very fresh";
      myRoot = parent._getRoot();
    }
    return myRoot;
  }

  @Nullable
  public DomElement getParent() {
    checkIsValid();

    final DomInvocationHandler handler = getParentHandler();
    return handler == null ? null : handler.getProxy();
  }

  protected final void checkIsValid() {
    if (!isValid()) {
      LOG.assertTrue(false, this + "\nclass=" + getClass() + "\nxml=" + getXmlElement());
    }
  }

  @Nullable
  final DomInvocationHandler getParentHandler() {
    return getParentStrategy().getParentHandler();
  }

  @NotNull
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
    myManager.fireEvent(new ElementChangedEvent(getProxy()));
  }

  public void copyFrom(DomElement other) {
    if (other == getProxy()) return;
    assert other.getDomElementType().equals(myType);

    if (other.getXmlElement() == null) {
      undefine();
      return;
    }

    final boolean wasInBulkChange = myManager.setBulkChange(true);
    try {
      ensureXmlElementExists();
      final DomGenericInfoEx genericInfo = getGenericInfo();
      for (final AttributeChildDescriptionImpl description : genericInfo.getAttributeChildrenDescriptions()) {
        description.getDomAttributeValue(this).setStringValue(description.getDomAttributeValue(other).getStringValue());
      }
      for (final DomFixedChildDescription description : genericInfo.getFixedChildrenDescriptions()) {
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
      for (final DomCollectionChildDescription description : genericInfo.getCollectionChildrenDescriptions()) {
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
    finally {
      myManager.setBulkChange(wasInBulkChange);
      if (!wasInBulkChange) {
        myManager.fireEvent(new ElementChangedEvent(myProxy));
      }
    }
  }

  public <T extends DomElement> T createStableCopy() {
    XmlTag tag = getXmlTag();
    if (tag != null && tag.isPhysical()) {
      final DomElement existing = myManager.getDomElement(tag);
      assert existing != null : existing + "\n---------\n" + tag.getParent().getText() + "\n-----------\n" + tag.getText();
      assert getProxy().equals(existing) : existing + "\n---------\n" + tag.getParent().getText() + "\n-----------\n" + tag.getText() + "\n----\n" + this + " != " +
                                           DomManagerImpl.getDomInvocationHandler(existing);
      final SmartPsiElementPointer<XmlTag> pointer = SmartPointerManager.getInstance(myManager.getProject()).createLazyPointer(tag);
      return myManager.createStableValue(new StableCopyFactory<T>(pointer, myType, getClass()));
    }
    return (T)createPathStableCopy();
  }

  protected DomElement createPathStableCopy() {
    throw new UnsupportedOperationException();
  }

  public final <T extends DomElement> T createMockCopy(final boolean physical) {
    final T copy = myManager.createMockElement((Class<? extends T>)getRawType(), getProxy().getModule(), physical);
    copy.copyFrom(getProxy());
    return copy;
  }

  @NotNull
  public String getXmlElementNamespace() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "this operation should be performed on the DOM having a physical parent, your DOM may be not very fresh";
    final XmlElement element = parent.getXmlElement();
    assert element != null;
    return getXmlName().getNamespace(element, getFile());
  }

  @Nullable
  public String getXmlElementNamespaceKey() {
    return getXmlName().getXmlName().getNamespaceKey();
  }

  public final Module getModule() {
    final Module module = ModuleUtil.findModuleForPsiElement(getFile());
    return module != null ? module : getRoot().getFile().getUserData(DomManagerImpl.MOCK_ELEMENT_MODULE);
  }

  public XmlTag ensureTagExists() {
    checkIsValid();

    XmlTag tag = getXmlTag();
    if (tag != null) return tag;

    tag = setEmptyXmlTag();
    setXmlElement(tag);

    myManager.fireEvent(new ElementDefinedEvent(getProxy()));
    addRequiredChildren();
    myManager.cacheHandler(tag, this);
    return getXmlTag();
  }

  public XmlElement getXmlElement() {
    return getParentStrategy().getXmlElement();
  }

  private DomParentStrategy getParentStrategy() {
    myParentStrategy = myParentStrategy.refreshStrategy(this);
    return myParentStrategy;
  }

  public XmlElement ensureXmlElementExists() {
    return ensureTagExists();
  }

  protected final XmlTag createChildTag(final EvaluatedXmlName tagName) {
    final String localName = tagName.getXmlName().getLocalName();
    if (localName.contains(":")) {
      try {
        return XmlElementFactory.getInstance(myManager.getProject()).createTagFromText("<" + localName + "/>");
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    final XmlElement element = getXmlElement();
    assert element != null;
    return getXmlTag().createChildTag(localName, tagName.getNamespace(element, getFile()), "", false);
  }

  public boolean isValid() {
    ProgressManager.getInstance().checkCanceled();
    return getParentStrategy().isValid();
  }

  @NotNull
  public final DomGenericInfoEx getGenericInfo() {
    return myGenericInfo;
  }

  protected abstract void undefineInternal();

  public final void undefine() {
    undefineInternal();
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
  }

  protected final void fireUndefinedEvent() {
    myManager.fireEvent(new ElementUndefinedEvent(getProxy()));
  }

  protected abstract XmlTag setEmptyXmlTag();

  protected void addRequiredChildren() {
    for (final AbstractDomChildrenDescription description : getGenericInfo().getChildrenDescriptions()) {
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
    return myTagName.getXmlName().getLocalName();
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
    ProgressManager.getInstance().checkCanceled();
    final DomElement element = getProxy();
    for (final AbstractDomChildrenDescription description : getGenericInfo().getChildrenDescriptions()) {
      for (final DomElement value : description.getValues(element)) {
        value.accept(visitor);
      }
    }
  }

  @NotNull
  protected final Converter getScalarConverter(final JavaMethod method) {
    final Converter converter;
    synchronized (myScalarConverters) {
      converter = myScalarConverters.get(method);
    }
    assert converter != null;
    return converter;
  }

  public final T getChildDescription() {
    return myChildDescription;
  }

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
                                 @Nullable Converter defaultConverter) {
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
      if (convertAnnotation instanceof ConvertAnnotationImpl) {
        return ((ConvertAnnotationImpl)convertAnnotation).getConverter();
      }
      return converterManager.getConverterInstance(convertAnnotation.value());
    }

    if (defaultConverter != null) {
      return defaultConverter;
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
      myFile = _getRoot().getFile();
    }
    return myFile;
  }

  @NotNull
  public DomNameStrategy getNameStrategy() {
    final Class<?> rawType = getRawType();
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(rawType, isAttribute());
    if (strategy != null) {
      return strategy;
    }
    final DomInvocationHandler handler = getParentHandler();
    return handler == null ? DomNameStrategy.HYPHEN_STRATEGY : handler.getNameStrategy();
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
      return new GetInvocation(getScalarConverter(method));
    }

    if (DomImplUtil.isTagValueSetter(method)) {
      return new SetInvocation(getScalarConverter(method));
    }

    return myGenericInfo.createInvocation(method);
  }

  @NotNull
  final IndexedElementInvocationHandler getFixedChild(final Pair<FixedChildDescriptionImpl, Integer> info) {
    final FixedChildDescriptionImpl description = info.first;
    final EvaluatedXmlName evaluatedXmlName = createEvaluatedXmlName(description.getXmlName());
    final XmlTag tag = getXmlTag();
    final int index = info.second;
    if (tag != null) {
      final List<XmlTag> tags = DomImplUtil.findSubTags(tag.getSubTags(), evaluatedXmlName, getFile());
      if (tags.size() > index) {
        XmlTag childTag = tags.get(index);
        DomInvocationHandler handler = myManager.getCachedHandler(childTag);
        if (!(handler instanceof IndexedElementInvocationHandler)) {
          handler = new IndexedElementInvocationHandler(evaluatedXmlName, description, index, new PhysicalDomParentStrategy(childTag), myManager, childTag.getNamespace());
          myManager.cacheHandler(childTag, handler);
        }
        return (IndexedElementInvocationHandler)handler;
      }
    }
    return new IndexedElementInvocationHandler(evaluatedXmlName, description, index, new VirtualDomParentStrategy(this), myManager, "");
  }

  @NotNull
  final AttributeChildInvocationHandler getAttributeChild(final AttributeChildDescriptionImpl description) {
    checkIsValid();
    final EvaluatedXmlName evaluatedXmlName = createEvaluatedXmlName(description.getXmlName());
    final XmlTag tag = getXmlTag();
    if (tag != null) {
      final XmlAttribute attribute = tag.getAttribute(description.getXmlName().getLocalName(), evaluatedXmlName.getNamespace(tag, getFile()));
      if (attribute != null) {
        AttributeChildInvocationHandler handler = (AttributeChildInvocationHandler)myManager.getCachedHandler(attribute);
        if (handler == null) {
          handler = new AttributeChildInvocationHandler(evaluatedXmlName, description, myManager, new PhysicalDomParentStrategy(attribute));
          myManager.cacheHandler(attribute, handler);
        }
        return handler;
      }

    }
    return new AttributeChildInvocationHandler(evaluatedXmlName, description, myManager, new VirtualDomParentStrategy(this));
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

  private static void setTagValue(final XmlTag tag, final String value) {
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

  protected final Class<?> getRawType() {
    return ReflectionUtil.getRawType(myType);
  }

  @Nullable
  public XmlTag getXmlTag() {
    return (XmlTag) getXmlElement();
  }

  @Nullable
  protected XmlElement recomputeXmlElement(@NotNull final DomInvocationHandler parentHandler) {
    return null;
  }

  protected final void detach() {
    final XmlElement element = getXmlElement();
    setXmlElement(null);
    if (element != null) myManager.cacheHandler(element, null);
  }

  protected final void setXmlElement(final XmlElement element) {
    refreshGenericInfo(element != null && !isAttribute());
    myParentStrategy = element == null ? myParentStrategy.clearXmlElement() : myParentStrategy.setXmlElement(element);
  }

  private void refreshGenericInfo(final boolean dynamic) {
    final StaticGenericInfo staticInfo = myManager.getStaticGenericInfo(myType);
    myGenericInfo = dynamic ? new DynamicGenericInfo(this, staticInfo, myManager.getProject()) : staticInfo;
  }

  @NotNull
  public final DomManagerImpl getManager() {
    return myManager;
  }

  public final DomElement addCollectionChild(final CollectionChildDescriptionImpl description, final Type type, int index) throws IncorrectOperationException {
    final EvaluatedXmlName name = createEvaluatedXmlName(description.getXmlName());
    final XmlTag tag = addEmptyTag(name, index);
    final CollectionElementInvocationHandler handler = new CollectionElementInvocationHandler(type, tag, description, this);
    myManager.fireEvent(new ElementChangedEvent(getProxy()));
    handler.addRequiredChildren();
    getManager().getTypeChooserManager().getTypeChooser(description.getType()).distinguishTag(tag, type);
    return handler.getProxy();
  }

  protected final void createFixedChildrenTags(EvaluatedXmlName tagName, FixedChildDescriptionImpl description, int count) {
    final XmlTag tag = ensureTagExists();
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, tagName, getFile());
    if (subTags.size() < count) {
      getFixedChild(Pair.create(description, count - 1)).ensureTagExists();
    }
  }

  private XmlTag addEmptyTag(final EvaluatedXmlName tagName, int index) throws IncorrectOperationException {
    final XmlTag tag = ensureTagExists();
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, tagName, getFile());
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

  @NotNull
  public final EvaluatedXmlName createEvaluatedXmlName(final XmlName xmlName) {
    return getXmlName().evaluateChildName(xmlName);
  }

  public List<? extends DomElement> getCollectionChildren(final AbstractCollectionChildDescription description, final NotNullFunction<DomInvocationHandler, List<XmlTag>> tagsGetter) {
    XmlTag tag = getXmlTag();
    if (tag == null) return Collections.emptyList();

    checkIsValid();
    final List<XmlTag> subTags = tagsGetter.fun(this);
    if (subTags.isEmpty()) return Collections.emptyList();

    List<DomElement> elements = new ArrayList<DomElement>(subTags.size());
    for (XmlTag subTag : subTags) {
      DomInvocationHandler handler = myManager.getCachedHandler(subTag);
      if (!(handler instanceof CollectionElementInvocationHandler)) {
        handler = new CollectionElementInvocationHandler(description.getType(), subTag, description, this);
        myManager.cacheHandler(subTag, handler);
      }
      elements.add(handler.getProxy());
    }
    return Collections.unmodifiableList(elements);
  }

  private static class StableCopyFactory<T extends DomElement> implements NullableFactory<T> {
    private final SmartPsiElementPointer<XmlTag> myPointer;
    private final Type myType;
    private final Class<? extends DomInvocationHandler> myHandlerClass;

    public StableCopyFactory(final SmartPsiElementPointer<XmlTag> pointer,
                             final Type type, final Class<? extends DomInvocationHandler> aClass) {
      myPointer = pointer;
      myType = type;
      myHandlerClass = aClass;
    }

    public T create() {
      final XmlTag tag = myPointer.getElement();
      if (tag == null || !tag.isValid()) return null;

      final DomElement element = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
      if (element == null || !element.getDomElementType().equals(myType)) return null;

      final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
      if (handler == null || !handler.getClass().equals(myHandlerClass)) return null;

      return (T)element;
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || !o.getClass().equals(getClass())) return false;

    final DomInvocationHandler that = (DomInvocationHandler)o;
    if (!myChildDescription.equals(that.myChildDescription)) return false;
    if (!getParentStrategy().equals(that.getParentStrategy())) return false;

    return true;
  }

  public int hashCode() {
    return myChildDescription.hashCode();
  }
}

