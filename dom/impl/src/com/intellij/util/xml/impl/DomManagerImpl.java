/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.WeakFactoryMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManagerImpl;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.problems.WolfTheProblemSolver;
import gnu.trove.THashMap;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class DomManagerImpl extends DomManager implements ProjectComponent {
  static final Key<Module> MODULE = Key.create("NameStrategy");
  static final Key<Object> MOCK = Key.create("MockElement");
  private static final Key<DomNameStrategy> NAME_STRATEGY_KEY = Key.create("NameStrategy");
  private static final Key<DomInvocationHandler> CACHED_HANDLER = Key.create("CachedInvocationHandler");
  private static final Key<DomFileElementImpl> CACHED_FILE_ELEMENT = Key.create("CachedFileElement");
  private static final Key<DomFileDescription> CACHED_FILE_DESCRIPTION = Key.create("CachedFileDescription");

  private final FactoryMap<Type, GenericInfoImpl> myMethodsMaps = new FactoryMap<Type, GenericInfoImpl>() {
    @NotNull
    protected GenericInfoImpl create(final Type type) {
      if (type instanceof Class) {
        return new GenericInfoImpl((Class<? extends DomElement>)type, DomManagerImpl.this);
      }
      if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType)type;
        return new GenericInfoImpl((Class<? extends DomElement>)parameterizedType.getRawType(), DomManagerImpl.this);
      }
      throw new AssertionError("Type not supported: " + type);
    }
  };

  private final EventDispatcher<DomEventListener> myListeners = EventDispatcher.create(DomEventListener.class);


  private final ConverterManagerImpl myConverterManager = new ConverterManagerImpl();
  private final FactoryMap<Pair<Type, Type>, InvocationCache> myInvocationCaches = new FactoryMap<Pair<Type, Type>, InvocationCache>() {
    @NotNull
    protected InvocationCache create(final Pair<Type, Type> key) {
      return new InvocationCache();
    }
  };
  private final Map<Class<? extends DomElement>, Class<? extends DomElement>> myImplementationClasses =
    new THashMap<Class<? extends DomElement>, Class<? extends DomElement>>();
  private final WeakFactoryMap<Class<? extends DomElement>, Class<? extends DomElement>> myCachedImplementationClasses =
    new WeakFactoryMap<Class<? extends DomElement>, Class<? extends DomElement>>() {
      protected Class<? extends DomElement> create(final Class<? extends DomElement> concreteInterface) {
        final TreeSet<Class<? extends DomElement>> set = new TreeSet<Class<? extends DomElement>>(CLASS_COMPARATOR);
        findImplementationClassDFS(concreteInterface, set);
        if (!set.isEmpty()) {
          return set.first();
        }
        final Implementation implementation = DomReflectionUtil.findAnnotationDFS(concreteInterface, Implementation.class);
        return implementation == null ? null : implementation.value();
      }

      private void findImplementationClassDFS(final Class concreteInterface, SortedSet<Class<? extends DomElement>> results) {
        Class<? extends DomElement> aClass = myImplementationClasses.get(concreteInterface);
        if (aClass != null) {
          results.add(aClass);
        }
        for (final Class aClass1 : ReflectionCache.getInterfaces(concreteInterface)) {
          findImplementationClassDFS(aClass1, results);
        }
      }

    };
  private final List<Function<DomElement, Collection<PsiElement>>> myPsiElementProviders =
    new ArrayList<Function<DomElement, Collection<PsiElement>>>();
  private final Set<DomFileDescription> myFileDescriptions = new HashSet<DomFileDescription>();
  private final Map<XmlFile, Object> myNonDomFiles = new WeakHashMap<XmlFile, Object>();
  private final FactoryMap<Class<? extends DomElementVisitor>, VisitorDescription> myVisitorDescriptions =
    new FactoryMap<Class<? extends DomElementVisitor>, VisitorDescription>() {
      @NotNull
      protected VisitorDescription create(final Class<? extends DomElementVisitor> key) {
        return new VisitorDescription(key);
      }
    };

  private Project myProject;
  private final DomElementAnnotationsManagerImpl myAnnotationsManager;
  private boolean myChanging;

  private final GenericValueReferenceProvider myGenericValueReferenceProvider = new GenericValueReferenceProvider();
  private final ReferenceProvidersRegistry myReferenceProvidersRegistry;
  private final PsiElementFactory myElementFactory;
  private static final Comparator<Class<? extends DomElement>> CLASS_COMPARATOR = new Comparator<Class<? extends DomElement>>() {
    public int compare(final Class<? extends DomElement> o1, final Class<? extends DomElement> o2) {
      if (o1.isAssignableFrom(o2)) return 1;
      if (o2.isAssignableFrom(o1)) return -1;
      if (o1.equals(o2)) return 0;
      throw new AssertionError("Incompatible implementation classes: " + o1 + " & " + o2);
    }
  };

  public DomManagerImpl(final PomModel pomModel,
                        final Project project,
                        final ReferenceProvidersRegistry registry,
                        final PsiManager psiManager,
                        final XmlAspect xmlAspect,
                        final WolfTheProblemSolver solver,
                        final DomElementAnnotationsManagerImpl annotationsManager) {
    myProject = project;
    myAnnotationsManager = annotationsManager;
    pomModel.addModelListener(new PomModelListener() {
      public synchronized void modelChanged(PomModelEvent event) {
        if (myChanging) return;
        final XmlChangeSet changeSet = (XmlChangeSet)event.getChangeSet(xmlAspect);
        if (changeSet != null) {
          new ExternalChangeProcessor(changeSet).processChanges();
        }
      }

      public boolean isAspectChangeInteresting(PomModelAspect aspect) {
        return xmlAspect.equals(aspect);
      }
    });
    myReferenceProvidersRegistry = registry;
    myElementFactory = psiManager.getElementFactory();
    solver.registerFileHighlightFilter(new Condition<VirtualFile>() {
      public boolean value(final VirtualFile object) {
        final PsiFile psiFile = psiManager.findFile(object);
        return psiFile instanceof XmlFile && getDomFileElement((XmlFile)psiFile) != null;
      }
    }, project);
  }

  public static DomManagerImpl getDomManager(Project project) {
    return (DomManagerImpl)project.getComponent(DomManager.class);
  }

  public void addDomEventListener(DomEventListener listener, Disposable parentDisposable) {
    myListeners.addListener(listener, parentDisposable);
  }

  public final ConverterManager getConverterManager() {
    return myConverterManager;
  }

  public void addPsiReferenceFactoryForClass(Class clazz, PsiReferenceFactory psiReferenceFactory) {
    myGenericValueReferenceProvider.addReferenceProviderForClass(clazz, psiReferenceFactory);
  }

  public ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }

  protected final void fireEvent(DomEvent event) {
    myListeners.getMulticaster().eventOccured(event);
  }

  public final GenericInfoImpl getGenericInfo(final Type type) {
    return myMethodsMaps.get(type);
  }

  final InvocationCache getInvocationCache(final Pair<Type, Type> type) {
    return myInvocationCaches.get(type);
  }

  @Nullable
  public static DomInvocationHandler getDomInvocationHandler(DomElement proxy) {
    final InvocationHandler handler = AdvancedProxy.getInvocationHandler(proxy);
    if (handler instanceof StableInvocationHandler) {
      final DomElement element = ((StableInvocationHandler)handler).getWrappedElement();
      return element == null ? null : getDomInvocationHandler(element);
    }
    if (handler instanceof DomInvocationHandler) {
      return (DomInvocationHandler)handler;
    }
    return null;
  }

  public static StableInvocationHandler getStableInvocationHandler(Object proxy) {
    return (StableInvocationHandler)AdvancedProxy.getInvocationHandler(proxy);
  }

  @Nullable
  final Class<? extends DomElement> getImplementation(final Class concreteInterface) {
    return myCachedImplementationClasses.get(concreteInterface);
  }

  public final Project getProject() {
    return myProject;
  }

  public static void setNameStrategy(final XmlFile file, final DomNameStrategy strategy) {
    file.putUserData(NAME_STRATEGY_KEY, strategy);
  }

  @NotNull
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(final XmlFile file, final Class<T> aClass, String rootTagName) {
    synchronized (PsiLock.LOCK) {
      DomFileElementImpl<T> element = getCachedElement(file);
      if (element == null) {
        element = new DomFileElementImpl<T>(file, aClass, rootTagName, this);
        setCachedElement(file, element);
      }
      return element;
    }
  }

  protected static void setCachedElement(final XmlFile file, final DomFileElementImpl element) {
    file.putUserData(CACHED_FILE_ELEMENT, element);
  }

  protected static void setCachedElement(final XmlTag tag, final DomInvocationHandler element) {
    if (tag != null) {
      tag.putUserData(CACHED_HANDLER, element);
    }
  }

  @Nullable
  public static DomFileElementImpl getCachedElement(final XmlFile file) {
    return file != null ? file.getUserData(CACHED_FILE_ELEMENT) : null;
  }

  @Nullable
  public static DomInvocationHandler getCachedElement(final XmlElement xmlElement) {
    return xmlElement.getUserData(CACHED_HANDLER);
  }

  @NotNull
  @NonNls
  public final String getComponentName() {
    return getClass().getName();
  }

  public final void runChange(Runnable change) {
    final boolean b = setChanging(true);
    try {
      change.run();
    }
    finally {
      setChanging(b);
    }
  }

  public final boolean setChanging(final boolean changing) {
    boolean oldChanging = myChanging;
    if (changing) {
      assert !oldChanging;
    }
    myChanging = changing;
    return oldChanging;
  }

  public final boolean isChanging() {
    return myChanging;
  }

  public final void initComponent() {
  }

  public final void disposeComponent() {
  }

  public final void projectOpened() {
  }

  public final void projectClosed() {
  }

  public <T extends DomElement> void registerImplementation(Class<T> domElementClass, Class<? extends T> implementationClass) {
    assert domElementClass.isAssignableFrom(implementationClass);
    myImplementationClasses.put(domElementClass, implementationClass);
    myCachedImplementationClasses.clear();
  }

  @Nullable
  public <T extends DomElement> DomFileElementImpl<T> getFileElement(XmlFile file) {
    final DomRootInvocationHandler element = getDomFileElement(file);
    return element == null? null : (DomFileElementImpl<T>)element.getRoot();
  }

  @Nullable
  public DomElement getDomElement(final XmlTag element) {
    final DomInvocationHandler handler = _getDomElement((XmlTag)element);
    return handler != null ? handler.getProxy() : null;
  }

  @Nullable
  public GenericAttributeValue getDomElement(final XmlAttribute element) {
    final XmlAttribute o = (XmlAttribute)element;
      final DomInvocationHandler handler = _getDomElement(o.getParent());
      return handler != null ? (GenericAttributeValue)handler.getAttributeChild(o.getLocalName()).getProxy() : null;
  }

  public final Collection<PsiElement> getPsiElements(final DomElement element) {
    return ContainerUtil
      .concat(myPsiElementProviders, new Function<Function<DomElement, Collection<PsiElement>>, Collection<PsiElement>>() {
        public Collection<PsiElement> fun(final Function<DomElement, Collection<PsiElement>> s) {
          return s.fun(element);
        }
      });
  }

  public void registerPsiElementProvider(final Function<DomElement, Collection<PsiElement>> provider, Disposable parentDisposable) {
    ContainerUtil.add(provider, myPsiElementProviders, parentDisposable);
  }

  @Nullable
  private DomInvocationHandler _getDomElement(final XmlTag tag) {
    if (tag == null) return null;

    DomInvocationHandler invocationHandler = getCachedElement(tag);
    if (invocationHandler != null && invocationHandler.isValid()) {
      return invocationHandler;
    }

    final XmlTag parentTag = tag.getParentTag();
    if (parentTag == null) {
      return getDomFileElement((XmlFile)tag.getContainingFile());
    }

    DomInvocationHandler parent = _getDomElement(parentTag);
    if (parent == null) return null;

    final GenericInfoImpl info = parent.getGenericInfo();
    final String tagName = tag.getName();
    final DomChildrenDescription childDescription = info.getChildDescription(tagName);
    if (childDescription == null) return null;

    childDescription.getValues(parent.getProxy());
    return getCachedElement(tag);
  }

  public final DomRootInvocationHandler getDomFileElement(final XmlFile xmlFile) {
    if (xmlFile != null && !myNonDomFiles.containsKey(xmlFile)) {
      DomFileElementImpl element = getCachedElement(xmlFile);
      if (element == null) {
        final XmlFile originalFile = (XmlFile)xmlFile.getOriginalFile();
        final DomInvocationHandler originalElement = getDomFileElement(originalFile);
        if (originalElement != null) {
          final Class<? extends DomElement> aClass =
            (Class<? extends DomElement>)DomReflectionUtil.getRawType(originalElement.getDomElementType());
          final String rootTagName = originalElement.getXmlElementName();
          return getFileElement(xmlFile, aClass, rootTagName).getRootHandler();
        }

        for (final DomFileDescription description : myFileDescriptions) {
          if (description.isMyFile(xmlFile)) {
            return getFileElement(xmlFile, description.getRootElementClass(), description.getRootTagName()).getRootHandler();
          }
        }

        myNonDomFiles.put(xmlFile, new Object());
      }

      if (element != null) {
        return element.getRootHandler();
      }
    }

    return null;
  }

  public final <T extends DomElement> T createMockElement(final Class<T> aClass, final Module module, final boolean physical) {
    final XmlFile file = (XmlFile)myElementFactory.createFileFromText("a.xml", StdFileTypes.XML, "", 0, physical);
    final DomFileElementImpl<T> fileElement = getFileElement(file, aClass, "root");
    fileElement.putUserData(MODULE, module);
    fileElement.putUserData(MOCK, new Object());
    return fileElement.getRootElement();
  }

  public final boolean isMockElement(DomElement element) {
    final DomFileElement<? extends DomElement> root = element.getRoot();
    return root.getUserData(MOCK) != null;
  }

  public final <T extends DomElement> T createStableValue(final Factory<T> provider) {
    final T initial = provider.create();
    final InvocationHandler handler = new StableInvocationHandler<T>(initial, provider);
    final Set<Class> intf = new HashSet<Class>();
    intf.addAll(Arrays.asList(initial.getClass().getInterfaces()));
    intf.add(StableElement.class);
    return AdvancedProxy.createProxy((Class<? extends T>)initial.getClass().getSuperclass(), intf.toArray(new Class[intf.size()]), handler,
                                     Collections.<JavaMethodSignature>emptySet());
  }

  public final void registerFileDescription(final DomFileDescription description, Disposable parentDisposable) {
    registerFileDescription(description);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        myFileDescriptions.remove(description);
      }
    });
  }

  public final void registerFileDescription(DomFileDescription description) {
    for (final Map.Entry<Class<? extends DomElement>, Class<? extends DomElement>> entry : ((DomFileDescription<?>)description).getImplementations().entrySet()) {
      registerImplementation((Class)entry.getKey(), entry.getValue());
    }
    final DomElementsAnnotator annotator = description.createAnnotator();
    final Class<? extends DomElement> rootClass = description.getRootElementClass();
    if (annotator != null) {
      myAnnotationsManager.registerDomElementsAnnotator(annotator, rootClass);
    }


    myFileDescriptions.add(description);
    registerReferenceProviders(rootClass);
  }

  private void registerReferenceProviders(final Class<? extends DomElement> rootClass) {
    final GenericInfoImpl info = getGenericInfo(rootClass);
    final Set<String> tagNames = info.getReferenceTagNames();
    if (!tagNames.isEmpty()) {
      myReferenceProvidersRegistry.registerXmlTagReferenceProvider(tagNames.toArray(new String[tagNames.size()]),
                                                                   new MyElementFilter(rootClass), true, myGenericValueReferenceProvider);
    }

    final Set<String> attributeNames = info.getReferenceAttributeNames();
    if (!attributeNames.isEmpty()) {
      myReferenceProvidersRegistry.registerXmlAttributeValueReferenceProvider(attributeNames.toArray(new String[attributeNames.size()]),
                                                                              new MyElementFilter(rootClass), true,
                                                                              myGenericValueReferenceProvider);
    }
  }

  @Nullable
  private DomFileDescription findFileDescription(DomElement element) {
    XmlFile file = element.getRoot().getFile();
    final DomFileDescription description = file.getUserData(CACHED_FILE_DESCRIPTION);
    if (description != null) {
      return description;
    }

    for (final DomFileDescription fileDescription : myFileDescriptions) {
      if (fileDescription.isMyFile(file)) {
        file.putUserData(CACHED_FILE_DESCRIPTION, fileDescription);
        return fileDescription;
      }
    }
    return null;
  }

  public final DomElement getResolvingScope(GenericDomValue element) {
    final DomFileDescription description = findFileDescription(element);
    return description == null ? element.getRoot() : description.getResolveScope(element);
  }

  public final DomElement getIdentityScope(DomElement element) {
    final DomFileDescription description = findFileDescription(element);
    return description == null ? element.getParent() : description.getIdentityScope(element);
  }

  public boolean processUsages(final Object target, DomElement scope, final Processor<PsiReference> processor) {
    final Class elementClass = target.getClass();
    final boolean[] stopped = new boolean[]{false};
    scope.accept(new DomElementVisitor() {
      public void visitGenericDomValue(GenericDomValue reference) {
        final XmlElement xmlElement = reference.getXmlElement();
        if (xmlElement == null) return;
        final Class parameter = DomUtil.getGenericValueParameter(reference.getDomElementType());
        if (parameter != null && ReflectionCache.isAssignable(parameter, elementClass) && target.equals(reference.getValue())) {
          for (final PsiReference psiReference : myGenericValueReferenceProvider.createReferences(reference, xmlElement)) {
            if (!processor.process(psiReference)) {
              stopped[0] = true;
              break;
            }
          }
          return;
        }
        visitDomElement(reference);
      }

      public void visitDomElement(DomElement element) {
        if (!stopped[0] && element.getXmlElement() != null) {
          element.acceptChildren(this);
        }
      }
    });
    return !stopped[0];
  }

  public boolean processUsages(Object target, XmlFile scope, Processor<PsiReference> processor) {
    final DomFileElementImpl<DomElement> element = getFileElement(scope);
    return element == null || processUsages(target, element, processor);
  }

  public final VisitorDescription getVisitorDescription(Class<? extends DomElementVisitor> aClass) {
    return myVisitorDescriptions.get(aClass);
  }

  private class MyElementFilter implements ElementFilter {
    private final Class<? extends DomElement> myRootClass;


    public MyElementFilter(final Class<? extends DomElement> rootClass) {
      myRootClass = rootClass;
    }

    public boolean isAcceptable(Object element, PsiElement context) {
      if (element instanceof XmlElement) {
        final PsiFile psiFile = ((PsiElement)element).getContainingFile();
        if (psiFile instanceof XmlFile) {
          final DomRootInvocationHandler handler = getDomFileElement((XmlFile)psiFile);
          if (handler != null && myRootClass.isAssignableFrom(DomReflectionUtil.getRawType(handler.getDomElementType()))) {
            return true;
          }
        }
      }
      return false;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }
}