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
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.xml.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.events.ElementChangedEvent;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManagerImpl;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public final class DomManagerImpl extends DomManager implements ProjectComponent {
  static final Key<Module> MODULE = Key.create("NameStrategy");
  static final Key<Object> MOCK = Key.create("MockElement");
  private static final Key<DomInvocationHandler> CACHED_HANDLER = Key.create("CachedInvocationHandler");
  private static final Key<FileDescriptionCachedValueProvider> CACHED_FILE_ELEMENT_PROVIDER = Key.create("CachedFileElementProvider");
  private static final Key<CachedValue> CACHED_FILE_ELEMENT_VALUE = Key.create("CachedFileElementValue");
  private static final Key<DomFileDescription> MOCK_DESCIPRTION = Key.create("MockDescription");
  private static final DomChangeAdapter MODIFICATION_TRACKER = new DomChangeAdapter() {
    protected void elementChanged(DomElement element) {
      if (element.isValid()) {
        ((DomFileElementImpl)element.getRoot()).onModified();
      }
    }
  };

  private final FactoryMap<Type, GenericInfoImpl> myMethodsMaps = new FactoryMap<Type, GenericInfoImpl>() {
    @NotNull
    protected GenericInfoImpl create(final Type type) {
      final Class<?> rawType = DomReflectionUtil.getRawType(type);
      assert rawType != null : "Type not supported: " + type;
      return new GenericInfoImpl(rawType, DomManagerImpl.this);
    }
  };
  private final FactoryMap<Pair<Type, Type>, InvocationCache> myInvocationCaches = new FactoryMap<Pair<Type, Type>, InvocationCache>() {
    @NotNull
    protected InvocationCache create(final Pair<Type, Type> key) {
      return new InvocationCache();
    }
  };
  private final FactoryMap<Class<? extends DomElementVisitor>, VisitorDescription> myVisitorDescriptions =
    new FactoryMap<Class<? extends DomElementVisitor>, VisitorDescription>() {
      @NotNull
      protected VisitorDescription create(final Class<? extends DomElementVisitor> key) {
        return new VisitorDescription(key);
      }
    };

  private final EventDispatcher<DomEventListener> myListeners = EventDispatcher.create(DomEventListener.class);
  private final ConverterManagerImpl myConverterManager = new ConverterManagerImpl();
  private final ImplementationClassCache myCachedImplementationClasses = new ImplementationClassCache();
  private final Map<DomFileDescription,Set<DomFileElementImpl>> myFileDescriptions = new THashMap<DomFileDescription,Set<DomFileElementImpl>>();
  private final FactoryMap<DomFileDescription,Set<DomFileDescription>> myFileDescriptionDependencies = new FactoryMap<DomFileDescription, Set<DomFileDescription>>() {
    protected Set<DomFileDescription> create(final DomFileDescription key) {
      final Class rootElementClass = key.getRootElementClass();
      final THashSet<DomFileDescription> result = new THashSet<DomFileDescription>();
      for (final DomFileDescription<?> description : myFileDescriptions.keySet()) {
        final Set<Class<? extends DomElement>> set = description.getDomModelDependencyItems();
        if (set.contains(rootElementClass)) {
          result.add(description);
        }
      }
      return result;
    }
  };
  private final GenericValueReferenceProvider myGenericValueReferenceProvider = new GenericValueReferenceProvider();
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();

  private final Project myProject;
  private final DomElementAnnotationsManagerImpl myAnnotationsManager;
  private final ReferenceProvidersRegistry myReferenceProvidersRegistry;
  private final PsiElementFactory myElementFactory;

  private long myModificationCount;
  private boolean myChanging;

  public DomManagerImpl(final PomModel pomModel,
                        final Project project,
                        final ReferenceProvidersRegistry registry,
                        final PsiManager psiManager,
                        final XmlAspect xmlAspect,
                        final WolfTheProblemSolver solver,
                        final DomElementAnnotationsManagerImpl annotationsManager,
                        final VirtualFileManager virtualFileManager) {
    myProject = project;
    myAnnotationsManager = annotationsManager;
    pomModel.addModelListener(new PomModelListener() {
      public synchronized void modelChanged(PomModelEvent event) {
        final XmlChangeSet changeSet = (XmlChangeSet)event.getChangeSet(xmlAspect);
        if (changeSet != null) {
          if (!myChanging) {
            new ExternalChangeProcessor(DomManagerImpl.this, changeSet).processChanges();
          }
          final XmlFile xmlFile = changeSet.getChangedFile();
          if (xmlFile == null) return;
          final FileDescriptionCachedValueProvider provider = getCachedValueProvider(xmlFile);
          if (provider != null) {
            final DomFileDescription description = provider.getFileDescription();
            if (description != null) {
              final DomFileElementImpl<DomElement> fileElement = getFileElement(xmlFile);
              final Set<XmlFile> toUpdate = new THashSet<XmlFile>();
              for (final DomFileDescription<?> domFileDescription : myFileDescriptionDependencies.get(description)) {
                toUpdate.addAll(domFileDescription.getDomModelDependentFiles(fileElement));
                toUpdate.addAll(ContainerUtil.map(myFileDescriptions.get(domFileDescription), new Function<DomFileElementImpl, XmlFile>() {
                  public XmlFile fun(final DomFileElementImpl s) {
                    return s.getFile();
                  }
                }));
              }
              for (final XmlFile file : toUpdate) {
                updateFileDomness(file, fileElement);                                 
              }
            }
          }
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

  private synchronized void updateFileDomness(final XmlFile file, DomFileElement changedRoot) {
    final FileDescriptionCachedValueProvider<DomElement> provider = getOrCreateCachedValueProvider(file);
    final DomFileElementImpl oldElement = provider.getOldValue();
    final boolean wasInModel = oldElement != null && provider.isInModel();
    
    provider.changed();

    final DomFileElementImpl<DomElement> fileElement = getFileElement(file);
    final boolean isInModel = fileElement != null && provider.getFileDescription().getDomModelDependentFiles(changedRoot).contains(file);
    provider.setInModel(isInModel);
    if (oldElement == null || fileElement == null) return;

    if (!isInModel && wasInModel) fireEvent(new ElementChangedEvent(oldElement), true);
    if (isInModel && !wasInModel) fireEvent(new ElementChangedEvent(fileElement), true);
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

  public final void addPsiReferenceFactoryForClass(Class clazz, PsiReferenceFactory psiReferenceFactory) {
    myGenericValueReferenceProvider.addReferenceProviderForClass(clazz, psiReferenceFactory);
  }

  public final ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }

  final void fireEvent(DomEvent event, final boolean modified) {
    myModificationCount++;
    if (modified) {
      event.accept(MODIFICATION_TRACKER);
    }
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
      description = new MockDomFileDescription<T>(aClass, rootTagName, file);
      registerFileDescription(description);
      file.putUserData(MOCK_DESCIPRTION, description);
    }
    final DomFileElementImpl<T> fileElement = getFileElement(file);
    assert fileElement != null;
    return fileElement;
  }

  final <T extends DomElement> DomFileElementImpl<T> createFileElement(final XmlFile file,
                                                                         final DomFileDescription<T> description) {
    return new DomFileElementImpl<T>(file, description.getRootElementClass(), description.getRootTagName(), this);
  }

  final <T extends DomElement> FileDescriptionCachedValueProvider<T> getOrCreateCachedValueProvider(XmlFile xmlFile) {
    FileDescriptionCachedValueProvider provider = getCachedValueProvider(xmlFile);
    if (provider == null) {
      xmlFile.putUserData(CACHED_FILE_ELEMENT_PROVIDER, provider = new FileDescriptionCachedValueProvider(this, xmlFile));
    }
    return provider;
  }

  static FileDescriptionCachedValueProvider getCachedValueProvider(final XmlFile xmlFile) {
    return xmlFile.getUserData(CACHED_FILE_ELEMENT_PROVIDER);
  }

  static void setCachedElement(final XmlTag tag, final DomInvocationHandler element) {
    if (tag != null) {
      tag.putUserData(CACHED_HANDLER, element);
    }
  }

  public final Map<DomFileDescription, Set<DomFileElementImpl>> getFileDescriptions() {
    return myFileDescriptions;
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

  final void runChange(Runnable change) {
    final boolean b = setChanging(true);
    try {
      change.run();
    }
    finally {
      setChanging(b);
    }
  }

  final boolean setChanging(final boolean changing) {
    boolean oldChanging = myChanging;
    if (changing) {
      assert !oldChanging;
    }
    myChanging = changing;
    return oldChanging;
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
    myCachedImplementationClasses.registerImplementation(domElementClass, implementationClass);
  }

  @Nullable
  public final synchronized <T extends DomElement> DomFileElementImpl<T> getFileElement(XmlFile file) {
    if (file == null) return null;

    CachedValue<DomFileElementImpl<T>> value = file.getUserData(CACHED_FILE_ELEMENT_VALUE);
    if (value == null) {
      final FileDescriptionCachedValueProvider<T> provider = getOrCreateCachedValueProvider(file);
      value = file.getManager().getCachedValuesManager().createCachedValue(provider, false);
      file.putUserData(CACHED_FILE_ELEMENT_VALUE, value);
    }
    return getCachedValueAndFireEvent(file, value);
  }

  @Nullable
  private static <T extends DomElement> DomFileElementImpl<T> getCachedValueAndFireEvent(XmlFile file, CachedValue<DomFileElementImpl<T>> value) {
    final DomFileElementImpl<T> element = value.getValue();
    getCachedValueProvider(file).fireEvents();
    return element;
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
        return getDomFileDescription((XmlFile)psiFile);
      }
    }
    return null;
  }

  private DomFileDescription getDomFileDescription(final XmlFile xmlFile) {
        if (getFileElement(xmlFile) != null) {
          return getCachedValueProvider(xmlFile).getFileDescription();
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
    assert initial != null;                    
    final StableInvocationHandler handler = new StableInvocationHandler<T>(initial, provider);
    final Set<Class> intf = new HashSet<Class>();
    intf.addAll(Arrays.asList(initial.getClass().getInterfaces()));
    intf.add(StableElement.class);
    final Class superClass = initial.getClass().getSuperclass();
    final T proxy = (T)AdvancedProxy.createProxy(superClass, intf.toArray(new Class[intf.size()]),
                                              handler, Collections.<JavaMethodSignature>emptySet());
    final Set<Class> classes = new HashSet<Class>();
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
    final Map<Class<? extends DomElement>, Class<? extends DomElement>> implementations =
      ((DomFileDescription<?>)description).getImplementations();
    for (final Map.Entry<Class<? extends DomElement>, Class<? extends DomElement>> entry : implementations.entrySet()) {
      registerImplementation((Class)entry.getKey(), entry.getValue());
    }
    myTypeChooserManager.copyFrom(description.getTypeChooserManager());

    final DomElementsAnnotator annotator = description.createAnnotator();
    if (annotator != null) {
      myAnnotationsManager.registerDomElementsAnnotator(annotator, description.getRootElementClass());
    }

    myFileDescriptions.put(description, new HashSet<DomFileElementImpl>());

    registerReferenceProviders(description);
  }

  private void registerReferenceProviders(final DomFileDescription description) {
    final FileDescriptionElementFilter fileDescriptionFilter = new FileDescriptionElementFilter(this, description);
    regiserLazyReferenceProvider(XmlTag.class, new DomLazyReferenceProvider(DomManagerImpl.this, description, myGenericValueReferenceProvider) {
      protected void registerTrueReferenceProvider(final String[] names) {
        myReferenceProvidersRegistry.registerXmlTagReferenceProvider(names, fileDescriptionFilter, true, myGenericValueReferenceProvider);
      }

      protected Set<String> getReferenceElementNames(final GenericInfoImpl info) {
        return info.getReferenceTagNames();
      }
    });

    regiserLazyReferenceProvider(XmlAttributeValue.class, new DomLazyReferenceProvider(DomManagerImpl.this, description, myGenericValueReferenceProvider) {
      protected void registerTrueReferenceProvider(final String[] names) {
        myReferenceProvidersRegistry.registerXmlAttributeValueReferenceProvider(names, fileDescriptionFilter, true, myGenericValueReferenceProvider);
      }

      protected Set<String> getReferenceElementNames(final GenericInfoImpl info) {
        return info.getReferenceAttributeNames();
      }
    });
  }

  private void regiserLazyReferenceProvider(final Class aClass, final DomLazyReferenceProvider tagReferenceProvider) {
    final FileDescriptionElementFilter filter = new FileDescriptionElementFilter(this, tagReferenceProvider.getDescription()) {
      protected boolean isInitialized() {
        return tagReferenceProvider.isInitialized();
      }
    };
    myReferenceProvidersRegistry.registerReferenceProvider(filter, aClass, tagReferenceProvider);
  }

  @Nullable
  private DomFileDescription findFileDescription(DomElement element) {
    final XmlFile file = element.getRoot().getFile();
    synchronized (PsiLock.LOCK) {
      return getOrCreateCachedValueProvider(file).getFileDescription();
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

  public long getModificationCount() {
    return myModificationCount;
  }

}