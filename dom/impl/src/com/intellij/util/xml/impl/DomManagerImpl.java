/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.WeakFactoryMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManagerImpl;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import com.intellij.util.xml.reflect.DomChildrenDescription;
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
  private static final Key<MyCachedValueProvider> CACHED_FILE_ELEMENT_PROVIDER = Key.create("CachedFileElementProvider");
  private static final Key<CachedValue<DomFileElementImpl>> CACHED_FILE_ELEMENT_VALUE = Key.create("CachedFileElementValue");
  private static final Key<DomFileDescription> MOCK_DESCIPRTION = Key.create("MockDescription");

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
  private static final DomChangeAdapter MODIFICATION_TRACKER = new DomChangeAdapter() {
    protected void elementChanged(DomElement element) {
      if (element.isValid()) {
        ((DomFileElementImpl)element.getRoot()).onModified();
      }
    }
  };
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();

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
          new ExternalChangeProcessor(DomManagerImpl.this, changeSet).processChanges();
        }
      }

      public boolean isAspectChangeInteresting(PomModelAspect aspect) {
        return xmlAspect.equals(aspect);
      }
    });
    myReferenceProvidersRegistry = registry;

    myElementFactory = psiManager.getElementFactory();
    solver.registerFileHighlightFilter(new Condition<VirtualFile>() {
      public boolean value(final VirtualFile file) {
        final PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
          public @Nullable PsiFile compute() {
            return psiManager.findFile(file);
          }
        });

        return isDomFile(psiFile);
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
    event.accept(MODIFICATION_TRACKER);
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
  public final <T extends DomElement> DomFileElementImpl<T> getOrCreateFileElement(final XmlFile file, final DomFileDescription<T> description) {
    synchronized (PsiLock.LOCK) {
      final DomFileElementImpl<T> element = getFileElement(file);
      return element != null ? element : createFileElement(file, description);
    }
  }

  @NotNull
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(final XmlFile file, final Class<T> aClass, String rootTagName) {
    DomFileDescription description = file.getUserData(MOCK_DESCIPRTION);
    if (description == null) {
      file.putUserData(MOCK_DESCIPRTION, description = new DomFileDescription<T>(aClass, rootTagName) {
        public boolean isMyFile(final XmlFile xmlFile) {
          return file == xmlFile;
        }

        protected void initializeFileDescription() {
        }
      });
      registerFileDescription(description);
    }
    return getFileElement(file);
  }

  private <T extends DomElement> DomFileElementImpl<T> createFileElement(final XmlFile file,
                                                                         final DomFileDescription<T> description) {
    return new DomFileElementImpl<T>(file, description.getRootElementClass(), description.getRootTagName(), this);
  }

  protected DomFileElementImpl getOrCreateCachedValue(XmlFile xmlFile) {
    CachedValue<DomFileElementImpl> value = xmlFile.getUserData(CACHED_FILE_ELEMENT_VALUE);
    if (value == null) {
      value = createCachedValue(xmlFile);
    }
    return value.getValue();
  }

  private CachedValue<DomFileElementImpl> createCachedValue(final XmlFile xmlFile) {
    final CachedValue<DomFileElementImpl> value =
      xmlFile.getManager().getCachedValuesManager().createCachedValue(getOrCreateCachedValueProvider(xmlFile), false);
    xmlFile.putUserData(CACHED_FILE_ELEMENT_VALUE, value);
    return value;
  }

  protected MyCachedValueProvider getOrCreateCachedValueProvider(XmlFile xmlFile) {
    MyCachedValueProvider provider = xmlFile.getUserData(CACHED_FILE_ELEMENT_PROVIDER);
    if (provider == null) {
      xmlFile.putUserData(CACHED_FILE_ELEMENT_PROVIDER, provider = new MyCachedValueProvider(xmlFile));
    }
    return provider;
  }

  protected static void setCachedElement(final XmlTag tag, final DomInvocationHandler element) {
    if (tag != null) {
      tag.putUserData(CACHED_HANDLER, element);
    }
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
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(XmlFile file) {
    if (file == null) return null;

    CachedValue<DomFileElementImpl> value = file.getUserData(CACHED_FILE_ELEMENT_VALUE);
    if (value == null) {
      value = file.getManager().getCachedValuesManager().createCachedValue(getOrCreateCachedValueProvider(file), false);
      file.putUserData(CACHED_FILE_ELEMENT_VALUE, value);
    }
    return value.getValue();
  }

  @Nullable
  public DomElement getDomElement(final XmlTag element) {
    final DomInvocationHandler handler = _getDomElement(element);
    return handler != null ? handler.getProxy() : null;
  }

  @Nullable
  public GenericAttributeValue getDomElement(final XmlAttribute attribute) {
    final DomInvocationHandler handler = _getDomElement(attribute.getParent());
    return handler != null ? (GenericAttributeValue)handler.getAttributeChild(attribute.getLocalName()).getProxy() : null;
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
      return getRootInvocationHandler((XmlFile)tag.getContainingFile());
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

  public final boolean isDomFile(@Nullable PsiFile file) {
    return file instanceof XmlFile && getFileElement((XmlFile)file) != null;
  }

  @Nullable
  public final DomFileDescription getDomFileDescription(PsiElement element) {
    if (element instanceof XmlElement) {
      final PsiFile psiFile = element.getContainingFile();
      if (psiFile instanceof XmlFile) {
        final XmlFile xmlFile = (XmlFile)psiFile;
        if (getFileElement(xmlFile) != null) {
          return getOrCreateCachedValueProvider(xmlFile).getFileDescription();
        }
      }
    }
    return null;
  }

  public final DomRootInvocationHandler getRootInvocationHandler(final XmlFile xmlFile) {
    if (xmlFile != null) {
      DomFileElementImpl element = getFileElement(xmlFile);
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
    final StableInvocationHandler handler = new StableInvocationHandler<T>(initial, provider);
    final Set<Class> intf = new HashSet<Class>();
    intf.addAll(Arrays.asList(initial.getClass().getInterfaces()));
    intf.add(StableElement.class);
    final Class<? extends T> superClass = (Class<? extends T>)initial.getClass().getSuperclass();
    final T proxy = AdvancedProxy.createProxy(superClass, intf.toArray(new Class[intf.size()]),
                                              handler, Collections.<JavaMethodSignature>emptySet());
    final Set classes = new HashSet();
    classes.addAll(Arrays.asList(initial.getClass().getInterfaces()));
    ContainerUtil.addIfNotNull(superClass, classes);
    handler.setClasses(classes);
    return proxy;
  }

  public final void registerFileDescription(final DomFileDescription description, Disposable parentDisposable) {
    registerFileDescription(description);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        myFileDescriptions.remove(description);
      }
    });
  }

  public final void registerFileDescription(final DomFileDescription description) {
    for (final Map.Entry<Class<? extends DomElement>, Class<? extends DomElement>> entry : ((DomFileDescription<?>)description).getImplementations().entrySet()) {
      registerImplementation((Class)entry.getKey(), entry.getValue());
    }
    myTypeChooserManager.copyFrom(description.getTypeChooserManager());
    final DomElementsAnnotator annotator = description.createAnnotator();
    final Class<? extends DomElement> rootClass = description.getRootElementClass();
    if (annotator != null) {
      myAnnotationsManager.registerDomElementsAnnotator(annotator, rootClass);
    }

    myFileDescriptions.add(description);

    final DomLazyReferenceProvider tagReferenceProvider = new DomLazyReferenceProvider(description) {
      protected void registerTrueReferenceProvider(final String[] names) {
        myReferenceProvidersRegistry.registerXmlTagReferenceProvider(names, new MyElementFilter(description), true,
                                                                     myGenericValueReferenceProvider);
      }

      protected Set<String> getReferenceElementNames(final GenericInfoImpl info) {
        return info.getReferenceTagNames();
      }
    };
    myReferenceProvidersRegistry.registerReferenceProvider(new MyElementFilter(description) {
      protected boolean isInitialized() {
        return tagReferenceProvider.myInitialized;
      }
    }, XmlTag.class, tagReferenceProvider);


    final DomLazyReferenceProvider attributeReferenceProvider = new DomLazyReferenceProvider(description) {
      protected void registerTrueReferenceProvider(final String[] names) {
        myReferenceProvidersRegistry.registerXmlAttributeValueReferenceProvider(names, new MyElementFilter(description), true,
                                                                                myGenericValueReferenceProvider);
      }

      protected Set<String> getReferenceElementNames(final GenericInfoImpl info) {
        return info.getReferenceAttributeNames();
      }
    };
    myReferenceProvidersRegistry.registerReferenceProvider(new MyElementFilter(description) {
      protected boolean isInitialized() {
        return attributeReferenceProvider.myInitialized;
      }
    }, XmlAttributeValue.class, attributeReferenceProvider);


  }



  @Nullable
  private DomFileDescription findFileDescription(DomElement element) {
    synchronized (PsiLock.LOCK) {
      return getOrCreateCachedValueProvider(element.getRoot().getFile()).getFileDescription();
    }
  }


  public final DomElement getResolvingScope(GenericDomValue element) {
    final DomFileDescription description = findFileDescription(element);
    return description == null ? element.getRoot() : description.getResolveScope(element);
  }

  public final DomElement getIdentityScope(DomElement element) {
    final DomFileDescription description = findFileDescription(element);
    return description == null ? element.getParent() : description.getIdentityScope(element);
  }

  public TypeChooserManager getTypeChooserManager() {
    return myTypeChooserManager;
  }

  public final VisitorDescription getVisitorDescription(Class<? extends DomElementVisitor> aClass) {
    return myVisitorDescriptions.get(aClass);
  }

  private class MyElementFilter implements ElementFilter {
    private final DomFileDescription myDescription;

    public MyElementFilter(final DomFileDescription description) {
      myDescription = description;
    }

    protected boolean isInitialized() {
      return false;
    }

    public boolean isAcceptable(Object element, PsiElement context) {
      return !isInitialized() && element instanceof XmlElement && getDomFileDescription((XmlElement)element) == myDescription;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return !isInitialized();
    }
  }

  private class MyCachedValueProvider implements CachedValueProvider<DomFileElementImpl> {
    private final XmlFile myXmlFile;
    private Result<DomFileElementImpl> myOldResult;
    private final Condition<DomFileDescription> myCondition = new Condition<DomFileDescription>() {
      public boolean value(final DomFileDescription description) {
        return description.isMyFile(myXmlFile);
      }
    };

    private DomFileDescription myFileDescription;

    public MyCachedValueProvider(final XmlFile xmlFile) {
      myXmlFile = xmlFile;
    }

    public final DomFileDescription getFileDescription() {
      return myFileDescription;
    }

    public Result<DomFileElementImpl> compute() {
      synchronized (PsiLock.LOCK) {
        if (myProject.isDisposed()) return new Result<DomFileElementImpl>(null);

        if (myOldResult != null && myFileDescription != null && myFileDescription.isMyFile(myXmlFile)) {
          return myOldResult;
        }

        final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
        if (originalFile != null) {
          return saveResult(getOrCreateCachedValueProvider(originalFile).getFileDescription());
        }

        return saveResult(ContainerUtil.find(myFileDescriptions, myCondition));
      }
    }

    private Result<DomFileElementImpl> saveResult(final DomFileDescription description) {
      myFileDescription = description;
      if (description == null) {
        final Set<Object> deps = new HashSet<Object>();
        deps.add(myXmlFile);
        for (final DomFileDescription fileDescription : myFileDescriptions) {
          deps.addAll(Arrays.asList(fileDescription.getDependencyItems(myXmlFile)));
        }
        return new Result<DomFileElementImpl>(null, deps.toArray());
      }

      DomFileElementImpl fileElement = new DomFileElementImpl(myXmlFile, description.getRootElementClass(), description.getRootTagName(), DomManagerImpl.this);
      return myOldResult = new Result<DomFileElementImpl>(fileElement, description.getDependencyItems(myXmlFile));
    }
  }

  private abstract class DomLazyReferenceProvider implements PsiReferenceProvider {
    private boolean myInitialized;
    private final DomFileDescription myDescription;

    public DomLazyReferenceProvider(final DomFileDescription description) {
      myDescription = description;
    }

    private boolean initialize(PsiElement element) {
      if (myInitialized || getDomFileDescription(element) != myDescription) {
        return false;
      }
      myInitialized = true;
      final GenericInfoImpl info = getGenericInfo(myDescription.getRootElementClass());
      final Set<String> tagNames = new HashSet<String>(getReferenceElementNames(info));
      if (!tagNames.isEmpty()) {
        registerTrueReferenceProvider(tagNames.toArray(new String[tagNames.size()]));
      }
      return true;
    }

    @NotNull
    public PsiReference[] getReferencesByElement(PsiElement element) {
      return initialize(element) ? myGenericValueReferenceProvider.getReferencesByElement(element) : PsiReference.EMPTY_ARRAY;
    }

    @NotNull
    public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
      return initialize(element) ? myGenericValueReferenceProvider.getReferencesByElement(element, type) : PsiReference.EMPTY_ARRAY;
    }

    @NotNull
    public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
      return initialize(position) ? myGenericValueReferenceProvider.getReferencesByString(str, position, type, offsetInPosition) : PsiReference.EMPTY_ARRAY;
    }

    public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    }

    protected abstract void registerTrueReferenceProvider(String[] names);

    protected abstract Set<String> getReferenceElementNames(GenericInfoImpl info);

  }
}