/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.events.ElementChangedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManagerImpl;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomManagerImpl");
  private static final Key<Object> MOCK = Key.create("MockElement");
  public static final Key<DomFileElementImpl> CACHED_FILE_ELEMENT = Key.create("CACHED_FILE_ELEMENT");
  static final Key<DomFileDescription> MOCK_DESCIPRTION = Key.create("MockDescription");
  static final Key<DomInvocationHandler> CACHED_DOM_HANDLER = Key.create("CACHED_DOM_HANDLER");

  private final FactoryMap<Type, StaticGenericInfo> myGenericInfos = new FactoryMap<Type, StaticGenericInfo>() {
    @NotNull
    protected StaticGenericInfo create(final Type type) {
      final Class<?> rawType = ReflectionUtil.getRawType(type);
      assert rawType != null : "Type not supported: " + type;
      return new StaticGenericInfo(rawType, DomManagerImpl.this);
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

  private final GenericValueReferenceProvider myGenericValueReferenceProvider = new GenericValueReferenceProvider();
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();

  private final Project myProject;
  private final DomApplicationComponent myApplicationComponent;
  private final DomElementAnnotationsManagerImpl myAnnotationsManager;
  private final PsiFileFactory myFileFactory;
  private final Map<XmlElement,Object> myHandlerCache = new ConcurrentHashMap<XmlElement, Object>();

  private long myModificationCount;
  private boolean myChanging;
  private final ProjectFileIndex myFileIndex;
  private boolean myBulkChange;

  public DomManagerImpl(final PomModel pomModel,
                        final Project project, final PsiManager psiManager,
                        final XmlAspect xmlAspect,
                        final WolfTheProblemSolver solver,
                        final DomElementAnnotationsManager annotationsManager,
                        final VirtualFileManager virtualFileManager,
                        final StartupManager startupManager,
                        final ProjectRootManager projectRootManager,
                        final DomApplicationComponent applicationComponent,
                        final CommandProcessor commandProcessor) {
    myProject = project;
    myApplicationComponent = applicationComponent;
    myAnnotationsManager = (DomElementAnnotationsManagerImpl)annotationsManager;
    pomModel.addModelListener(new PomModelListener() {
      public void modelChanged(PomModelEvent event) {
        final XmlChangeSet changeSet = (XmlChangeSet)event.getChangeSet(xmlAspect);
        if (changeSet != null) {
          if (!myChanging) {
            new ExternalChangeProcessor(DomManagerImpl.this, changeSet).processChanges();
          }
        }
      }

      public boolean isAspectChangeInteresting(PomModelAspect aspect) {
        return xmlAspect.equals(aspect);
      }
    }, project);

    project.getMessageBus().connect().subscribe(ProjectTopics.MODIFICATION_TRACKER, new PsiModificationTracker.Listener() {
      public void modificationCountChanged() {
        ApplicationManager.getApplication().assertWriteAccessAllowed();
        if (!myBulkChange) {
          myHandlerCache.clear();
        }
      }
    });

    myFileFactory = PsiFileFactory.getInstance(project);
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
          private final List<XmlFile> myDeletedFiles = new SmartList<XmlFile>();

          public void contentsChanged(VirtualFileEvent event) {
            processVfsChange(event.getFile());
          }

          public void fileCreated(VirtualFileEvent event) {
            processVfsChange(event.getFile());
          }

          public void beforeFileDeletion(final VirtualFileEvent event) {
            beforeFileDeletion(event.getFile());
          }

          private void beforeFileDeletion(final VirtualFile file) {
            if (project.isDisposed()) return;

            if (file.isDirectory() && file instanceof NewVirtualFile) {
              for (final VirtualFile child : ((NewVirtualFile)file).getCachedChildren()) {
                beforeFileDeletion(child);
              }
              return;
            }

            if (StdFileTypes.XML.equals(file.getFileType())) {
              final PsiFile psiFile = psiManager.findFile(file);
              if (psiFile instanceof XmlFile) {
                myDeletedFiles.add((XmlFile)psiFile);
              }
            }
          }

          public void fileDeleted(VirtualFileEvent event) {
            if (!myDeletedFiles.isEmpty()) {
              if (!project.isDisposed()) {
                for (final XmlFile file : myDeletedFiles) {
                  processXmlFileChange(file, true);
                }
              }
              myDeletedFiles.clear();
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
      _registerFileDescription(description);
    }
  }

  private void processVfsChange(final VirtualFile file) {
    if (myFileIndex.isInContent(file)) {
      processFileOrDirectoryChange(file);
    }
  }

  public DomInvocationHandler getCachedHandler(XmlElement element) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return (DomInvocationHandler)myHandlerCache.get(element);
  }

  public void cacheHandler(XmlElement element, DomInvocationHandler handler) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (handler != null) {
      myHandlerCache.put(element, handler);
    } else {
      myHandlerCache.remove(element);
    }
    element.putUserData(CACHED_DOM_HANDLER, handler);
  }

  private void processFileChange(final VirtualFile file) {
    if (StdFileTypes.XML != file.getFileType()) return;
    processFileChange(PsiManager.getInstance(myProject).findFile(file));
  }

  private void processFileChange(final PsiFile file) {
    if (file != null && StdFileTypes.XML.equals(file.getFileType()) && file instanceof XmlFile) {
      processXmlFileChange((XmlFile)file, true);
    }
  }

  private boolean processXmlFileChange(@NotNull final XmlFile file, boolean fireChanged) {
    if (!fireChanged) return false;

    final DomEvent[] list = recomputeFileElement(file);
    for (final DomEvent event : list) {
      fireEvent(event);
    }
    return list.length > 0;
  }

  final DomEvent[] recomputeFileElement(final XmlFile file) {
    final DomFileElementImpl oldElement = getCachedFileElement(file);
    final DomFileElementImpl<DomElement> newElement = getFileElement(file);
    if (newElement == null) {
      return oldElement == null ? DomEvent.EMPTY_ARRAY : new DomEvent[]{new ElementUndefinedEvent(oldElement)};
    }

    if (oldElement == null) return new DomEvent[]{new ElementDefinedEvent(newElement)};
    if (oldElement.equals(newElement)) return new DomEvent[]{new ElementChangedEvent(newElement)};
    return new DomEvent[]{new ElementUndefinedEvent(oldElement), new ElementDefinedEvent(newElement)};
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
    if (myBulkChange) return;
    myModificationCount++;
    myListeners.getMulticaster().eventOccured(event);
  }

  public final DomGenericInfo getGenericInfo(final Type type) {
    return getStaticGenericInfo(type);
  }

  public final StaticGenericInfo getStaticGenericInfo(final Type type) {
    synchronized (myGenericInfos) {
      return myGenericInfos.get(type);
    }
  }

  final InvocationCache getInvocationCache(final Pair<Type, Type> type) {
    synchronized (myInvocationCaches) {
      return myInvocationCaches.get(type);
    }
  }

  @Nullable
  public static DomInvocationHandler getDomInvocationHandler(DomElement proxy) {
    if (proxy instanceof DomFileElement) {
      return null;
    }
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
    if (file.getUserData(MOCK_DESCIPRTION) == null) {
      file.putUserData(MOCK_DESCIPRTION, new MockDomFileDescription<T>(aClass, rootTagName, file));
      myHandlerCache.clear();
    }
    final DomFileElementImpl<T> fileElement = getFileElement(file);
    assert fileElement != null;
    return fileElement;
  }


  @SuppressWarnings({"unchecked"})
  @NotNull
  final <T extends DomElement> FileDescriptionCachedValueProvider<T> getOrCreateCachedValueProvider(final XmlFile xmlFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    FileDescriptionCachedValueProvider provider = (FileDescriptionCachedValueProvider)myHandlerCache.get(xmlFile);
    if (provider == null) {
      myHandlerCache.put(xmlFile, provider = new FileDescriptionCachedValueProvider(this, xmlFile));
    }
    return provider;
  }

  public final Set<DomFileDescription> getFileDescriptions(String rootTagName) {
    return myApplicationComponent.getFileDescriptions(rootTagName);
  }

  public final Set<DomFileDescription> getAcceptingOtherRootTagNameDescriptions() {
    return myApplicationComponent.getAcceptingOtherRootTagNameDescriptions();
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
  static <T extends DomElement> DomFileElementImpl<T> getCachedFileElement(XmlFile file) {
    return file.getUserData(CACHED_FILE_ELEMENT);
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
    if (myChanging) return null;

    final DomInvocationHandler handler = getDomHandler(element);
    return handler != null ? handler.getProxy() : null;
  }

  @Nullable
  public GenericAttributeValue getDomElement(final XmlAttribute attribute) {
    if (myChanging) return null;

    final DomInvocationHandler handler = getDomHandler(attribute.getParent());
    if (handler == null) return null;

    final String localName = attribute.getLocalName();
    for (final AttributeChildDescriptionImpl description : handler.getGenericInfo().getAttributeChildrenDescriptions()) {
      if (description.getXmlName().getLocalName().equals(localName)) {
        final GenericAttributeValue value = description.getDomAttributeValue(handler);
        if (attribute.equals(value.getXmlAttribute())) {
          return value;
        }
      }
    }
    return null;
  }

  @Nullable
  DomInvocationHandler getDomHandler(final XmlTag tag) {
    if (tag == null) return null;

    DomInvocationHandler invocationHandler = getCachedHandler(tag);
    if (invocationHandler != null) return invocationHandler;

    final XmlTag parentTag = tag.getParentTag();
    if (parentTag == null) {
      final PsiFile psiFile = tag.getContainingFile();
      if (psiFile instanceof XmlFile) {
        final DomRootInvocationHandler handler = getRootInvocationHandler((XmlFile)psiFile);
        return handler != null && handler.getXmlTag() == tag ? handler : null;
      }
      return null;
    }

    DomInvocationHandler parent = getDomHandler(parentTag);
    if (parent == null) return null;

    final AbstractDomChildrenDescription childDescription = findChildrenDescription(tag, parent);
    if (childDescription == null) return null;

    childDescription.getValues(parent.getProxy());
    return getCachedHandler(tag);
  }

  @Nullable
  public AbstractDomChildrenDescription findChildrenDescription(@NotNull final XmlTag tag, @NotNull final DomElement parent) {
    return findChildrenDescription(tag, getDomInvocationHandler(parent));
  }

  private static AbstractDomChildrenDescription findChildrenDescription(final XmlTag tag, final DomInvocationHandler parent) {
    final DomGenericInfoEx info = parent.getGenericInfo();
    return info.findChildrenDescription(parent, tag.getLocalName(), tag.getNamespace(), false, tag.getName());
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
    final DomFileElementImpl<DomElement> element = getFileElement(xmlFile);
    return element != null ? element.getFileDescription() : null;
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
    final XmlFile file = (XmlFile)myFileFactory.createFileFromText("a.xml", StdFileTypes.XML, "", (long)0, physical);
    file.putUserData(MOCK_ELEMENT_MODULE, module);
    file.putUserData(MOCK, new Object());
    return getFileElement(file, aClass, "I_sincerely_hope_that_nobody_will_have_such_a_root_tag_name").getRootElement();
  }

  public final boolean isMockElement(DomElement element) {
    final DomFileElement<? extends DomElement> root = element.getRoot();
    return root.getFile().getUserData(MOCK) != null;
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
        getFileDescriptions(description.getRootTagName()).remove(description);
        getAcceptingOtherRootTagNameDescriptions().remove(description);
      }
    });
  }

  public final void registerFileDescription(final DomFileDescription description) {
    _registerFileDescription(description);

    myApplicationComponent.registerFileDescription(description);
  }

  private void _registerFileDescription(final DomFileDescription description) {
    myHandlerCache.clear();

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
  }

  @NotNull
  public final DomElement getResolvingScope(GenericDomValue element) {
    final DomFileDescription description = element.getRoot().getFileDescription();
    return description.getResolveScope(element);
  }

  @Nullable
  public final DomElement getIdentityScope(DomElement element) {
    final DomFileDescription description = element.getRoot().getFileDescription();
    return description.getIdentityScope(element);
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

  public boolean setBulkChange(final boolean bulkChange) {
    final boolean oldValue = myBulkChange;
    myBulkChange = bulkChange;
    if (!bulkChange) {
      myHandlerCache.clear();
      myModificationCount++;
    }
    return oldValue;
  }
}
