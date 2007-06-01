/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.*;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManagerImpl;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public final class DomManagerImpl extends DomManager implements ProjectComponent {
  private static final Key<Object> MOCK = Key.create("MockElement");
  private static final Key<DomInvocationHandler> CACHED_HANDLER = Key.create("CachedInvocationHandler");
  private static final Key<FileDescriptionCachedValueProvider> CACHED_FILE_ELEMENT_PROVIDER = Key.create("CachedFileElementProvider");
  private static final Key<DomFileDescription> MOCK_DESCIPRTION = Key.create("MockDescription");
  private static final DomEventAdapter MODIFICATION_TRACKER = new DomChangeAdapter() {

    public void elementDefined(ElementDefinedEvent event) {
      final DomElement element = event.getElement();
      if (element.isValid() && element instanceof DomFileElementImpl) {
        ((DomFileElementImpl)element).onModified();
        return;
      }

      super.elementDefined(event);
    }

    protected void elementChanged(DomElement element) {
      if (element.isValid()) {
        ((DomFileElementImpl)element.getRoot()).onModified();
      }
    }
  };

  private final FactoryMap<Type, GenericInfoImpl> myGenericInfos = new FactoryMap<Type, GenericInfoImpl>() {
    @NotNull
    protected GenericInfoImpl create(final Type type) {
      final Class<?> rawType = ReflectionUtil.getRawType(type);
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
  private final ConcurrentFactoryMap<Class<? extends DomElementVisitor>, VisitorDescription> myVisitorDescriptions =
    new ConcurrentFactoryMap<Class<? extends DomElementVisitor>, VisitorDescription>() {
      @NotNull
      protected VisitorDescription create(final Class<? extends DomElementVisitor> key) {
        return new VisitorDescription(key);
      }
    };

  private final EventDispatcher<DomEventListener> myListeners = EventDispatcher.create(DomEventListener.class);
  private final ConverterManagerImpl myConverterManager = new ConverterManagerImpl();
  private final ImplementationClassCache myCachedImplementationClasses = new ImplementationClassCache();
  private final Map<DomFileDescription,Set<WeakReference<DomFileElementImpl>>> myFileDescriptions = new THashMap<DomFileDescription,Set<WeakReference<DomFileElementImpl>>>();
  private final FactoryMap<String,Set<DomFileDescription>> myRootTagName2FileDescription = new FactoryMap<String, Set<DomFileDescription>>() {
    protected Set<DomFileDescription> create(final String key) {
      return new THashSet<DomFileDescription>();
    }
  };
  private final Set<DomFileDescription> myAcceptingOtherRootTagNamesDescriptions = new THashSet<DomFileDescription>();
  private final GenericValueReferenceProvider myGenericValueReferenceProvider = new GenericValueReferenceProvider();
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();

  private final Project myProject;
  private final DomElementAnnotationsManagerImpl myAnnotationsManager;
  private final ReferenceProvidersRegistry myReferenceProvidersRegistry;
  private final PsiElementFactory myElementFactory;

  private long myModificationCount;
  private boolean myChanging;
  private final ProjectFileIndex myFileIndex;

  public DomManagerImpl(final PomModel pomModel,
                        final Project project,
                        final ReferenceProvidersRegistry registry,
                        final PsiManager psiManager,
                        final XmlAspect xmlAspect,
                        final WolfTheProblemSolver solver,
                        final DomElementAnnotationsManager annotationsManager,
                        final VirtualFileManager virtualFileManager,
                        final StartupManager startupManager,
                        final ProjectRootManager projectRootManager) {
    myProject = project;
    myAnnotationsManager = (DomElementAnnotationsManagerImpl)annotationsManager;
    pomModel.addModelListener(new PomModelListener() {
      public void modelChanged(PomModelEvent event) {
        final XmlChangeSet changeSet = (XmlChangeSet)event.getChangeSet(xmlAspect);
        if (changeSet != null && !myChanging) {
          new ExternalChangeProcessor(DomManagerImpl.this, changeSet).processChanges();
        }
      }

      public boolean isAspectChangeInteresting(PomModelAspect aspect) {
        return xmlAspect.equals(aspect);
      }
    }, project);
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

    startupManager.registerStartupActivity(new Runnable() {
      public void run() {
        final VirtualFileAdapter listener = new VirtualFileAdapter() {
          public void contentsChanged(VirtualFileEvent event) {
            processVfsChange(event.getFile());
          }

          public void fileCreated(VirtualFileEvent event) {
            processVfsChange(event.getFile());
          }

          public void fileDeleted(VirtualFileEvent event) {
            List<XmlFile> toChange = null;
            for (final Set<WeakReference<DomFileElementImpl>> set : myFileDescriptions.values()) {
              for (Iterator<WeakReference<DomFileElementImpl>> it = set.iterator(); it.hasNext();) {
                WeakReference<DomFileElementImpl> reference = it.next();
                final DomFileElementImpl element = reference.get();
                if (element == null) {
                  it.remove();
                  continue;
                }
                final XmlFile file = element.getFile();
                if (!file.isValid()) {
                  it.remove();
                  if (toChange == null) toChange = new ArrayList<XmlFile>();
                  toChange.add(file);
                }
              }
            }
            if (toChange != null) {
              for (final XmlFile file : toChange) {
                processFileChange(file);
              }
            }
          }

          public void propertyChanged(VirtualFilePropertyEvent event) {
            final VirtualFile file = event.getFile();
            if (!file.isDirectory() && VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
              processVfsChange(file);
            }
          }
        };
        virtualFileManager.addVirtualFileListener(listener, project);
      }
    });

    myFileIndex = projectRootManager.getFileIndex();

    for (final DomFileDescription description : Extensions.getExtensions(DomFileDescription.EP_NAME)) {
      registerFileDescription(description);
    }
  }

  private void processVfsChange(final VirtualFile file) {
    if (myFileIndex.isInContent(file)) {
      processFileOrDirectoryChange(file);
    }
  }

  private void processFileChange(final VirtualFile file) {
    if (StdFileTypes.XML != file.getFileType()) return;
    processFileChange(PsiManager.getInstance(myProject).findFile(file));
  }

  private void processFileChange(final PsiFile file) {
    if (file != null && StdFileTypes.XML.equals(file.getFileType()) && file instanceof XmlFile) {
      for (final DomEvent event : recomputeFileElement(file)) {
        fireEvent(event);
      }
    }
  }

  final List<DomEvent> recomputeFileElement(final PsiFile file) {
    return getOrCreateCachedValueProvider((XmlFile)file).computeFileElement(true);
  }

  private void processDirectoryChange(final VirtualFile directory) {
    for (final VirtualFile file : directory.getChildren()) {
      processFileOrDirectoryChange(file);
    }
  }

  private void processFileOrDirectoryChange(final VirtualFile file) {
    if (!file.isDirectory()) {
      processFileChange(file);
    } else {
      processDirectoryChange(file);
    }
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
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

  final void fireEvent(DomEvent event) {
    incModificationCount();
    event.accept(MODIFICATION_TRACKER);
    myListeners.getMulticaster().eventOccured(event);
  }

  public void incModificationCount() {
    myModificationCount++;
  }

  public final GenericInfoImpl getGenericInfo(final Type type) {
    synchronized (myGenericInfos) {
      return myGenericInfos.get(type);
    }
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
    //noinspection unchecked
    return myCachedImplementationClasses.get(concreteInterface);
  }

  public final Project getProject() {
    return myProject;
  }

  @NotNull
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(final XmlFile file, final Class<T> aClass, String rootTagName) {
    //noinspection unchecked
    DomFileDescription<T> description = file.getUserData(MOCK_DESCIPRTION);
    if (description == null) {
      description = new MockDomFileDescription<T>(aClass, rootTagName, file);
      registerFileDescription(description);
      file.putUserData(MOCK_DESCIPRTION, description);
    }
    final DomFileElementImpl<T> fileElement = getFileElement(file);
    assert fileElement != null;
    return fileElement;
  }


  private final UserDataCache<FileDescriptionCachedValueProvider, XmlFile, Object> myCachedFileElementCache =
    new UserDataCache<FileDescriptionCachedValueProvider, XmlFile, Object>() {
      protected FileDescriptionCachedValueProvider compute(final XmlFile xmlFile, Object o) {
        return new FileDescriptionCachedValueProvider(DomManagerImpl.this, xmlFile);
      }
    };


  @SuppressWarnings({"unchecked"})
  @NotNull
  final <T extends DomElement> FileDescriptionCachedValueProvider<T> getOrCreateCachedValueProvider(XmlFile xmlFile) {
    return (FileDescriptionCachedValueProvider<T>)myCachedFileElementCache.get(CACHED_FILE_ELEMENT_PROVIDER, xmlFile, null);
  }

  static void setCachedElement(final XmlTag tag, final DomInvocationHandler element) {
    if (tag != null) {
      tag.putUserData(CACHED_HANDLER, element);
    }
  }

  public final Set<String> getRootTagNames() {
    return myRootTagName2FileDescription.keySet();
  }

  public final Set<DomFileDescription> getFileDescriptions(String rootTagName) {
    return myRootTagName2FileDescription.get(rootTagName);
  }

  public final Set<DomFileDescription> getAcceptingOtherRootTagNameDescriptions() {
    return myAcceptingOtherRootTagNamesDescriptions;
  }

  public final Map<DomFileDescription, Set<WeakReference<DomFileElementImpl>>> getFileDescriptions() {
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

  public final void registerImplementation(Class<? extends DomElement> domElementClass, Class<? extends DomElement> implementationClass) {
    myCachedImplementationClasses.registerImplementation(domElementClass, implementationClass);
  }

  public final void clearImplementations() {
    myCachedImplementationClasses.clear();
  }

  @Nullable
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(XmlFile file) {
    if (file == null) return null;
    if (!StdFileTypes.XML.equals(file.getFileType())) return null;
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && virtualFile.isDirectory()) return null;
    return this.<T>getOrCreateCachedValueProvider(file).getFileElement();
  }



  @Nullable
  final <T extends DomElement> DomFileElementImpl<T> getCachedFileElement(XmlFile file) {
    if (file == null) return null;
    if (!StdFileTypes.XML.equals(file.getFileType())) return null;
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && virtualFile.isDirectory()) return null;
    return this.<T>getOrCreateCachedValueProvider(file).getLastValue();
  }

  @Nullable
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(XmlFile file, Class<T> domClass) {
    final DomFileDescription description = getDomFileDescription(file);
    if (description != null && ReflectionCache.isAssignable(domClass, description.getRootElementClass())) {
      return getFileElement(file);
    }
    return null;
  }

  @Nullable
  public final DomElement getDomElement(final XmlTag element) {
    final DomInvocationHandler handler = _getDomElement(element);
    return handler != null ? handler.getProxy() : null;
  }

  @Nullable
  public GenericAttributeValue getDomElement(final XmlAttribute attribute) {
    final DomInvocationHandler handler = _getDomElement(attribute.getParent());
    if (handler == null) return null;
    for (final AttributeChildDescriptionImpl description : handler.getGenericInfo().getAttributeChildrenDescriptions()) {
      final GenericAttributeValue value = description.getDomAttributeValue(handler);
      if (attribute.equals(value.getXmlAttribute())) {
        return value;
      }
    }
    return null;
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
      final PsiFile psiFile = tag.getContainingFile();
      return psiFile instanceof XmlFile ? getRootInvocationHandler((XmlFile)psiFile) : null;
    }

    DomInvocationHandler parent = _getDomElement(parentTag);
    if (parent == null) return null;

    final GenericInfoImpl info = parent.getGenericInfo();
    final DomChildrenDescription childDescription = info.findChildrenDescription(parent, tag.getLocalName(), tag.getNamespace(), false, tag.getName());
    if (childDescription == null) return null;

    childDescription.getValues(parent.getProxy());
    return getCachedElement(tag);
  }

  public final boolean isDomFile(@Nullable PsiFile file) {
    return file instanceof XmlFile && getFileElement((XmlFile)file) != null;
  }

  @Nullable
  public final DomFileDescription<?> getDomFileDescription(PsiElement element) {
    if (element instanceof XmlElement) {
      final PsiFile psiFile = element.getContainingFile();
      if (psiFile instanceof XmlFile) {
        return getDomFileDescription((XmlFile)psiFile);
      }
    }
    return null;
  }

  @Nullable
  public final DomFileDescription<?> getDomFileDescription(final XmlFile xmlFile) {
    if (getFileElement(xmlFile) != null) {
      return getOrCreateCachedValueProvider(xmlFile).getFileDescription();
    }
    return null;
  }

  @Nullable
  private DomRootInvocationHandler getRootInvocationHandler(final XmlFile xmlFile) {
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
    //final DomFileElementImpl<T> fileElement = getFileElement(file, aClass, "root");
    final DomFileElementImpl<T> fileElement = getFileElement(file, aClass, "I_sincerely_hope_that_nobody_will_have_such_a_root_tag_name");
    fileElement.putUserData(MOCK_ELEMENT_MODULE, module);
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
    //noinspection unchecked

    return (T)AdvancedProxy.createProxy(initial.getClass().getSuperclass(), intf.toArray(new Class[intf.size()]),
                                                 handler, Collections.<JavaMethodSignature>emptySet());
  }

  public final <T extends DomElement> void registerFileDescription(final DomFileDescription<T> description, Disposable parentDisposable) {
    registerFileDescription(description);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        myFileDescriptions.remove(description);
        myRootTagName2FileDescription.get(description.getRootTagName()).remove(description);
      }
    });
  }

  public final void registerFileDescription(final DomFileDescription description) {
    //noinspection unchecked
    final Map<Class<? extends DomElement>, Class<? extends DomElement>> implementations = description.getImplementations();
    for (final Map.Entry<Class<? extends DomElement>, Class<? extends DomElement>> entry : implementations.entrySet()) {
      registerImplementation(entry.getKey(), entry.getValue());
    }
    myTypeChooserManager.copyFrom(description.getTypeChooserManager());

    final DomElementsAnnotator annotator = description.createAnnotator();
    if (annotator != null) {
      //noinspection unchecked
      myAnnotationsManager.registerDomElementsAnnotator(annotator, description.getRootElementClass());
    }

    myFileDescriptions.put(description, new HashSet<WeakReference<DomFileElementImpl>>());
    myRootTagName2FileDescription.get(description.getRootTagName()).add(description);
    if (description.acceptsOtherRootTagNames()) {
      myAcceptingOtherRootTagNamesDescriptions.add(description);
    }

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
    return getOrCreateCachedValueProvider(element.getRoot().getFile()).getFileDescription();
  }

  @NotNull
  public final DomElement getResolvingScope(GenericDomValue element) {
    final DomFileDescription description = findFileDescription(element);
    return description == null ? element.getRoot() : description.getResolveScope(element);
  }

  @Nullable
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
