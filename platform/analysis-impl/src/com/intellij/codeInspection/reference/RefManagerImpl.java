// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.reference;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.lang.Language;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtilCore;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class RefManagerImpl extends RefManager {
  public static final ExtensionPointName<RefGraphAnnotator> EP_NAME = ExtensionPointName.create("com.intellij.refGraphAnnotator");
  private static final Logger LOG = Logger.getInstance(RefManager.class);

  private long myLastUsedMask = 0b1000_00000000_00000000_00000000; // guarded by this

  private final @NotNull Project myProject;
  private AnalysisScope myScope;
  private RefProject myRefProject;

  private final Set<VirtualFile> myUnprocessedFiles = VfsUtilCore.createCompactVirtualFileSet();
  private final boolean processExternalElements = Registry.is("batch.inspections.process.external.elements");
  private final ConcurrentHashMap<PsiAnchor, RefElement> myRefTable = new ConcurrentHashMap<>();

  private volatile List<RefElement> myCachedSortedRefs; // holds cached values from myPsiToRefTable/myRefTable sorted by containing virtual file; benign data race

  private final ConcurrentMap<Module, RefModule> myModules = new ConcurrentHashMap<>();
  private final ProjectIterator myProjectIterator = new ProjectIterator();
  private final AtomicBoolean myDeclarationsFound = new AtomicBoolean(false);
  private final PsiManager myPsiManager;

  private volatile boolean myIsInProcess;
  private volatile boolean myOfflineView;

  private final List<RefGraphAnnotator> myGraphAnnotators = ContainerUtil.createConcurrentList();
  private GlobalInspectionContext myContext;

  private final Map<Key<?>, RefManagerExtension<?>> myExtensions = new HashMap<>();
  private final Map<Language, RefManagerExtension<?>> myLanguageExtensions = new HashMap<>();
  private final Interner<String> myNameInterner = Interner.createStringInterner();

  private volatile BlockingQueue<Runnable> myTasks;
  private volatile List<Future<?>> myFutures;

  public RefManagerImpl(@NotNull Project project, @Nullable AnalysisScope scope, @NotNull GlobalInspectionContext context) {
    myProject = project;
    myScope = scope;
    myContext = context;
    myPsiManager = PsiManager.getInstance(project);
    myRefProject = new RefProjectImpl(this);
    for (InspectionExtensionsFactory factory : InspectionExtensionsFactory.EP_NAME.getExtensionList()) {
      final RefManagerExtension<?> extension = factory.createRefManagerExtension(this);
      if (extension != null) {
        myExtensions.put(extension.getID(), extension);
        for (Language language : extension.getLanguages()) {
          myLanguageExtensions.put(language, extension);
        }
      }
    }
    if (scope != null) {
      for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
        getRefModule(module);
      }
    }
  }

  String internName(@NotNull String name) {
    synchronized (myNameInterner) {
      return myNameInterner.intern(name);
    }
  }

  public @NotNull GlobalInspectionContext getContext() {
    return myContext;
  }

  @Override
  public void iterate(@NotNull RefVisitor visitor) {
    for (RefElement refElement : getSortedElements()) {
      refElement.accept(visitor);
    }
    for (RefModule refModule : myModules.values()) {
      if (myScope.containsModule(refModule.getModule())) refModule.accept(visitor);
    }
    for (RefManagerExtension<?> extension : myExtensions.values()) {
      extension.iterate(visitor);
    }
  }

  public void cleanup() {
    myScope = null;
    myRefProject = null;
    myRefTable.clear();
    myCachedSortedRefs = null;
    myModules.clear();
    myContext = null;

    myGraphAnnotators.clear();
    for (RefManagerExtension<?> extension : myExtensions.values()) {
      extension.cleanup();
    }
    myExtensions.clear();
    myLanguageExtensions.clear();
  }

  @Override
  public @Nullable AnalysisScope getScope() {
    return myScope;
  }

  private void fireNodeInitialized(RefElement refElement) {
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onInitialize(refElement);
    }
  }

  public void fireNodeMarkedReferenced(RefElement refWhat,
                                       RefElement refFrom,
                                       boolean referencedFromClassInitializer,
                                       final boolean forReading,
                                       final boolean forWriting) {
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onMarkReferenced(refWhat, refFrom, referencedFromClassInitializer, forReading, forWriting);
    }
  }

  public void fireNodeMarkedReferenced(RefElement refWhat,
                                       RefElement refFrom,
                                       boolean referencedFromClassInitializer,
                                       final boolean forReading,
                                       final boolean forWriting,
                                       PsiElement element) {
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onMarkReferenced(refWhat, refFrom, referencedFromClassInitializer, forReading, forWriting, element);
    }
  }

  public void fireNodeMarkedReferenced(PsiElement what, PsiElement from) {
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onMarkReferenced(what, from, false);
    }
  }

  public void fireBuildReferences(RefElement refElement) {
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onReferencesBuild(refElement);
    }
  }

  public void registerGraphAnnotator(@NotNull RefGraphAnnotator annotator) {
    if (!myGraphAnnotators.contains(annotator)) {
      myGraphAnnotators.add(annotator);
      if (annotator instanceof RefGraphAnnotatorEx) {
        ((RefGraphAnnotatorEx)annotator).initialize(this);
      }
    }
  }

  public void unregisterAnnotator(RefGraphAnnotator annotator) {
    myGraphAnnotators.remove(annotator);
  }

  @Override
  public synchronized long getLastUsedMask() {
    if (myLastUsedMask < 0) {
      throw new IllegalStateException("We're out of 64 bits, sorry");
    }
    myLastUsedMask <<= 1;
    return myLastUsedMask;
  }

  @Override
  public <T> T getExtension(final @NotNull Key<T> key) {
    //noinspection unchecked
    return (T)myExtensions.get(key);
  }

  @Override
  public @Nullable String getType(final @NotNull RefEntity ref) {
    for (RefManagerExtension<?> extension : myExtensions.values()) {
      final String type = extension.getType(ref);
      if (type != null) return type;
    }
    if (ref instanceof RefFile) {
      return SmartRefElementPointer.FILE;
    }
    if (ref instanceof RefModule) {
      return SmartRefElementPointer.MODULE;
    }
    if (ref instanceof RefProject) {
      return SmartRefElementPointer.PROJECT;
    }
    if (ref instanceof RefDirectory) {
      return SmartRefElementPointer.DIR;
    }
    return null;
  }

  @Override
  public @NotNull RefEntity getRefinedElement(@NotNull RefEntity ref) {
    for (RefManagerExtension<?> extension : myExtensions.values()) {
      ref = extension.getRefinedElement(ref);
    }
    return ref;
  }

  @Override
  public @Nullable Element export(@NotNull RefEntity refEntity, final int actualLine) {
    refEntity = getRefinedElement(refEntity);

    Element problem = new Element("problem");

    if (refEntity instanceof RefDirectory) {
      Element fileElement = new Element("file");
      VirtualFile virtualFile = ((PsiDirectory)((RefDirectory)refEntity).getPsiElement()).getVirtualFile();
      fileElement.addContent(virtualFile.getUrl());
      problem.addContent(fileElement);
    }
    else if (refEntity instanceof RefElement) {
      final RefElement refElement = (RefElement)refEntity;
      final SmartPsiElementPointer<?> pointer = refElement.getPointer();
      PsiFile psiFile = pointer.getContainingFile();
      if (psiFile == null) return null;

      Element fileElement = new Element("file");
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      fileElement.addContent(virtualFile.getUrl());
      problem.addContent(fileElement);

      int resultLine;
      if (actualLine == -1) {
        final Document document = PsiDocumentManager.getInstance(pointer.getProject()).getDocument(psiFile);
        LOG.assertTrue(document != null);
        final Segment range = pointer.getRange();
        resultLine = range == null ? -1 : document.getLineNumber(range.getStartOffset()) + 1;
      }
      else {
        resultLine = actualLine + 1;
      }

      Element lineElement = new Element("line");
      lineElement.addContent(String.valueOf(resultLine));
      problem.addContent(lineElement);

      appendModule(problem, refElement.getModule());
    }
    else if (refEntity instanceof RefModule) {
      final RefModule refModule = (RefModule)refEntity;
      final VirtualFile moduleFile = refModule.getModule().getModuleFile();
      final Element fileElement = new Element("file");
      fileElement.addContent(moduleFile != null ? moduleFile.getUrl() : refEntity.getName());
      problem.addContent(fileElement);
      appendModule(problem, refModule);
    }

    for (RefManagerExtension<?> extension : myExtensions.values()) {
      extension.export(refEntity, problem);
    }

    new SmartRefElementPointerImpl(refEntity, true).writeExternal(problem);
    return problem;
  }

  @Override
  @Nullable
  public Element export(@NotNull RefEntity entity) {
    Element element = export(entity, -1);
    if (element == null) return null;

    if (!(entity instanceof RefElement)) return element;

    SmartPsiElementPointer<?> pointer = ((RefElement)entity).getPointer();
    PsiFile psiFile = pointer.getContainingFile();

    if (psiFile == null) return element;

    Document document = PsiDocumentManager.getInstance(pointer.getProject()).getDocument(psiFile);
    if (document == null) return element;

    Segment range = pointer.getRange();
    if (range == null) return element;

    int firstRangeLine = document.getLineNumber(range.getStartOffset());
    int lineStartOffset = document.getLineStartOffset(firstRangeLine);
    int endOffset = Math.min(range.getEndOffset(), document.getLineEndOffset(firstRangeLine));

    TextRange exportedRange = new TextRange(range.getStartOffset(), endOffset);
    String text = ProblemDescriptorUtil.extractHighlightedText(exportedRange, psiFile);

    element.addContent(new Element("offset").addContent(String.valueOf(exportedRange.getStartOffset() - lineStartOffset)));
    element.addContent(new Element("length").addContent(String.valueOf(exportedRange.getLength())));
    element.addContent(new Element("highlighted_element").addContent(ProblemDescriptorUtil.sanitizeIllegalXmlChars(text)));

    return element;
  }

  @Override
  public @Nullable String getGroupName(final @NotNull RefElement entity) {
    for (RefManagerExtension<?> extension : myExtensions.values()) {
      final String groupName = extension.getGroupName(entity);
      if (groupName != null) return groupName;
    }

    RefEntity parent = entity.getOwner();
    while (parent != null && !(parent instanceof RefDirectory)) {
      parent = parent.getOwner();
    }
    final LinkedList<String> containingDirs = new LinkedList<>();
    while (parent instanceof RefDirectory) {
      containingDirs.addFirst(parent.getName());
      parent = parent.getOwner();
    }
    return containingDirs.isEmpty() ? null : StringUtil.join(containingDirs, File.separator);
  }

  private static void appendModule(final Element problem, final RefModule refModule) {
    if (refModule != null) {
      Element moduleElement = new Element("module");
      moduleElement.addContent(refModule.getName());
      problem.addContent(moduleElement);
    }
  }

  public void findAllDeclarations() {
    if (!myDeclarationsFound.getAndSet(true)) {
      long before = System.currentTimeMillis();
      startTaskRunners();
      final AnalysisScope scope = getScope();
      if (scope != null) {
        scope.accept(myProjectIterator);
      }
      waitForTasksToComplete();
      LOG.info("Total duration of processing project usages: " + (System.currentTimeMillis() - before) + "ms");
    }
  }

  private void waitForTasksToComplete() {
    final List<Future<?>> futures = myFutures;
    if (futures == null) return;
    myFutures = null;
    try {
      for (Future<?> future : futures) {
        future.get();
      }
    }
    catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void executeTask(Runnable runnable) {
    if (myTasks != null) {
      try {
        myTasks.put(runnable);
      }
      catch (InterruptedException ignore) {}
    }
    else {
      runnable.run();
    }
  }

  private void startTaskRunners() {
    if (!Registry.is("batch.inspections.process.project.usages.in.parallel")) {
      return;
    }
    final int threadsCount = Math.min(4, Runtime.getRuntime().availableProcessors() - 1);
    if (threadsCount == 0) {
      // need more than 1 core for parallel processing
      return;
    }
    LOG.info("Processing project usages using " + threadsCount + " threads");
    // unbounded queue because tasks are submitted under read action, so we mustn't block
    myTasks = new LinkedBlockingQueue<>();
    myFutures = new ArrayList<>();
    final Application application = ApplicationManager.getApplication();
    final ProgressManager progressManager = ProgressManager.getInstance();
    final ProgressIndicator indicator = progressManager.getProgressIndicator();
    for (int i = 0; i < (threadsCount > 0 ? threadsCount : 4) ; i++) {
      final Future<?> future = application.executeOnPooledThread(() -> {
        while (myTasks != null) {
          if (myFutures == null && myTasks.isEmpty()) return;
          try {
            final Runnable task = myTasks.poll(50, TimeUnit.MILLISECONDS);
            if (task != null) {
              DumbService.getInstance(myProject).runReadActionInSmartMode(
                () -> progressManager.executeProcessUnderProgress(task, indicator)
              );
            }
          }
          catch (InterruptedException ignore) {
          }
        }
      });
      myFutures.add(future);
    }
  }

  public boolean isDeclarationsFound() {
    return myDeclarationsFound.get();
  }

  public void runInsideInspectionReadAction(@NotNull Runnable runnable) {
    myIsInProcess = true;
    try {
      runnable.run();
    }
    finally {
      myTasks = null; // remove any pending tasks
      waitForTasksToComplete();
      myIsInProcess = false;
      if (myScope != null) {
        myScope.invalidate();
      }
      myCachedSortedRefs = null;
    }
  }

  public void startOfflineView() {
    myOfflineView = true;
  }

  public boolean isOfflineView() {
    return myOfflineView;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull RefProject getRefProject() {
    return myRefProject;
  }

  public @NotNull List<RefElement> getSortedElements() {
    List<RefElement> answer = myCachedSortedRefs;
    if (answer != null) return answer;

    answer = new ArrayList<>(myRefTable.values());
    List<RefElement> list = answer;
    ReadAction.run(() -> ContainerUtil.quickSort(list, (o1, o2) -> {
      VirtualFile v1 = ((RefElementImpl)o1).getVirtualFile();
      VirtualFile v2 = ((RefElementImpl)o2).getVirtualFile();
      if (!Objects.equals(v1, v2)) {
        return (v1==null?"":v1.getPath()).compareTo(v2==null?"":v2.getPath());
      }
      Segment r1 = ObjectUtils.notNull(o1.getPointer().getRange(), TextRange.EMPTY_RANGE);
      Segment r2 = ObjectUtils.notNull(o2.getPointer().getRange(), TextRange.EMPTY_RANGE);
      return Segment.BY_START_OFFSET_THEN_END_OFFSET.compare(r1, r2);
    }));
    myCachedSortedRefs = answer = Collections.unmodifiableList(answer);
    return answer;
  }

  @Override
  public @NotNull PsiManager getPsiManager() {
    return myPsiManager;
  }

  @Override
  public synchronized boolean isInGraph(VirtualFile file) {
    return !myUnprocessedFiles.contains(file);
  }

  @Override
  public @Nullable PsiNamedElement getContainerElement(@NotNull PsiElement element) {
    Language language = element.getLanguage();
    RefManagerExtension<?> extension = myLanguageExtensions.get(language);
    if (extension == null) return null;
    return extension.getElementContainer(element);
  }

  private synchronized void registerUnprocessed(VirtualFile virtualFile) {
    myUnprocessedFiles.add(virtualFile);
  }

  private void removeReference(@NotNull RefElement refElem) {
    final PsiElement element = refElem.getPsiElement();
    final RefManagerExtension<?> extension = element != null ? getExtension(element.getLanguage()) : null;
    if (extension != null) {
      extension.removeReference(refElem);
    }

    if (element != null &&
        myRefTable.remove(createAnchor(element)) != null) return;

    //PsiElement may have been invalidated and new one returned by getElement() is different so we need to do this stuff.
    for (Map.Entry<PsiAnchor, RefElement> entry : myRefTable.entrySet()) {
      RefElement value = entry.getValue();
      PsiAnchor anchor = entry.getKey();
      if (value == refElem) {
        myRefTable.remove(anchor);
        break;
      }
    }
    myCachedSortedRefs = null;
  }

  private static @NotNull PsiAnchor createAnchor(final @NotNull PsiElement element) {
    return ReadAction.compute(() -> PsiAnchor.create(element));
  }

  public void initializeAnnotators() {
    for (RefGraphAnnotator annotator : EP_NAME.getExtensionList()) {
      registerGraphAnnotator(annotator);
    }
  }

  private class ProjectIterator extends PsiElementVisitor {

    @Override
    public void visitElement(@NotNull PsiElement element) {
      ProgressManager.checkCanceled();
      final RefManagerExtension<?> extension = getExtension(element.getLanguage());
      if (extension != null) {
        extension.visitElement(element);
      }
      else if (processExternalElements) {
        PsiFile file = element.getContainingFile();
        if (file != null) {
          RefManagerExtension<?> externalFileManagerExtension =
            ContainerUtil.find(myExtensions.values(), ex -> ex.shouldProcessExternalFile(file));
          if (externalFileManagerExtension == null) {
            if (element instanceof PsiFile) {
              VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
              if (virtualFile instanceof VirtualFileWithId) {
                registerUnprocessed(virtualFile);
              }
            }
          } else {
            RefElement refFile = getReference(file);
            LOG.assertTrue(refFile != null, file);
            for (PsiReference reference : element.getReferences()) {
              PsiElement resolve = reference.resolve();
              if (resolve != null) {
                fireNodeMarkedReferenced(resolve, file);
                RefElement refWhat = getReference(resolve);
                if (refWhat == null) {
                  PsiFile targetContainingFile = resolve.getContainingFile();
                  //no logic to distinguish different elements in the file anyway
                  if (file == targetContainingFile) continue;
                  refWhat = getReference(targetContainingFile);
                }

                if (refWhat != null) {
                  ((RefElementImpl)refWhat).addInReference(refFile);
                  ((RefElementImpl)refFile).addOutReference(refWhat);
                }
              }
            }

            Stream<? extends PsiElement> implicitRefs = externalFileManagerExtension.extractExternalFileImplicitReferences(file);
            implicitRefs.forEach(e -> {
              RefElement superClassReference = getReference(e);
              if (superClassReference != null) {
                //in case of implicit inheritance, e.g. GroovyObject
                //= no explicit reference is provided, dependency on groovy library could be treated as redundant though it is not
                //inReference is not important in this case
                ((RefElementImpl)refFile).addOutReference(superClassReference);
              }
            });

            if (element instanceof PsiFile) {
              externalFileManagerExtension.markExternalReferencesProcessed(refFile);
            }
          }
        }
      }
      for (PsiElement aChildren : element.getChildren()) {
        aChildren.accept(this);
      }
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        String relative = ProjectUtilCore.displayUrlRelativeToProject(virtualFile, virtualFile.getPresentableUrl(), myProject, true, false);
        myContext.incrementJobDoneAmount(myContext.getStdJobDescriptors().BUILD_GRAPH, relative);
      }
      if (file instanceof PsiBinaryFile || file.getFileType().isBinary()) {
        return;
      }
      final FileViewProvider viewProvider = file.getViewProvider();
      final Set<Language> relevantLanguages = viewProvider.getLanguages();
      for (Language language : relevantLanguages) {
        try {
          visitElement(viewProvider.getPsi(language));
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          throw e;
        }
        catch (Throwable e) {
          if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
            LOG.error(file.getName(), e);
          }
          else {
            LOG.error(new RuntimeExceptionWithAttachments(e, new Attachment("diagnostics.txt", file.getName())));
          }
        }
      }
      myPsiManager.dropResolveCaches();
    }
  }

  @Override
  public @Nullable RefElement getReference(@Nullable PsiElement elem) {
    return getReference(elem, false);
  }

  public @Nullable RefElement getReference(PsiElement elem, final boolean ignoreScope) {
    if (ReadAction.compute(() -> elem == null || !elem.isValid() ||
                                 elem instanceof LightElement || !(elem instanceof PsiDirectory) && !belongsToScope(elem, ignoreScope))) {
      return null;
    }

    return getFromRefTableOrCache(
      elem,
      () -> ReadAction.compute(() -> {
        final RefManagerExtension<?> extension = getExtension(elem.getLanguage());
        if (extension != null) {
          final RefElement refElement = extension.createRefElement(elem);
          if (refElement != null) return (RefElementImpl)refElement;
        }
        if (elem instanceof PsiFile) {
          return new RefFileImpl((PsiFile)elem, this);
        }
        if (elem instanceof PsiDirectory) {
          return new RefDirectoryImpl((PsiDirectory)elem, this);
        }
        return null;
      }),
      element -> {
        element.initialize();
        element.setInitialized(true);
        for (RefManagerExtension<?> each : myExtensions.values()) {
          each.onEntityInitialized(element, elem);
        }
        fireNodeInitialized(element);
      });
  }

  private RefManagerExtension<?> getExtension(final Language language) {
    return myLanguageExtensions.get(language);
  }

  @Override
  public @Nullable RefEntity getReference(final String type, final String fqName) {
    for (RefManagerExtension<?> extension : myExtensions.values()) {
      final RefEntity refEntity = extension.getReference(type, fqName);
      if (refEntity != null) return refEntity;
    }
    if (SmartRefElementPointer.FILE.equals(type)) {
      return RefFileImpl.fileFromExternalName(this, fqName);
    }
    if (SmartRefElementPointer.MODULE.equals(type)) {
      return RefModuleImpl.moduleFromName(this, fqName);
    }
    if (SmartRefElementPointer.PROJECT.equals(type)) {
      return getRefProject();
    }
    if (SmartRefElementPointer.DIR.equals(type)) {
      String url = VfsUtilCore.pathToUrl(PathMacroManager.getInstance(getProject()).expandPath(fqName));
      VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
      if (vFile != null) {
        final PsiDirectory dir = PsiManager.getInstance(getProject()).findDirectory(vFile);
        return getReference(dir);
      }
    }
    return null;
  }

  public @Nullable <T extends RefElement> T getFromRefTableOrCache(final @NotNull PsiElement element, @NotNull NullableFactory<? extends T> factory) {
    return getFromRefTableOrCache(element, factory, null);
  }

  private @Nullable <T extends RefElement> T getFromRefTableOrCache(@NotNull PsiElement element,
                                                                    @NotNull NullableFactory<? extends T> factory,
                                                                    @Nullable Consumer<? super T> whenCached) {
    PsiAnchor psiAnchor = createAnchor(element);
    //noinspection unchecked
    T result = (T)myRefTable.get(psiAnchor);
    if (result != null) return result;

    if (!isValidPointForReference()) {
      //LOG.assertTrue(true, "References may become invalid after process is finished");
      return null;
    }

    T newElement = factory.create();
    if (newElement == null) return null;

    myCachedSortedRefs = null;
    RefElement prev = myRefTable.putIfAbsent(psiAnchor, newElement);
    if (prev != null) {
      //noinspection unchecked
      return (T)prev;
    }
    if (whenCached != null) {
      ReadAction.nonBlocking(() -> whenCached.consume(newElement)).executeSynchronously();
    }

    return newElement;
  }

  @Override
  public RefModule getRefModule(@Nullable Module module) {
    if (module == null) {
      return null;
    }
    RefModule refModule = myModules.get(module);
    if (refModule == null) {
      refModule = ConcurrencyUtil.cacheOrGet(myModules, module, new RefModuleImpl(module, this));
    }
    return refModule;
  }

  @Override
  public boolean belongsToScope(final PsiElement psiElement) {
    return belongsToScope(psiElement, false);
  }

  private boolean belongsToScope(final PsiElement psiElement, final boolean ignoreScope) {
    if (psiElement == null || !psiElement.isValid()) return false;
    if (psiElement instanceof PsiCompiledElement) return false;
    final PsiFile containingFile = ReadAction.compute(psiElement::getContainingFile);
    if (containingFile == null) {
      return false;
    }
    for (RefManagerExtension<?> extension : myExtensions.values()) {
      if (!extension.belongsToScope(psiElement)) return false;
    }
    final Boolean inProject = ReadAction.compute(() -> psiElement.getManager().isInProject(psiElement));
    return inProject.booleanValue() && (ignoreScope || getScope() == null || getScope().contains(psiElement));
  }

  @Override
  public String getQualifiedName(RefEntity refEntity) {
    if (refEntity == null || refEntity instanceof RefElementImpl && !refEntity.isValid()) {
      return AnalysisBundle.message("inspection.reference.invalid");
    }

    return refEntity.getQualifiedName();
  }

  @Override
  public void removeRefElement(@NotNull RefElement refElement, @NotNull List<? super RefElement> deletedRefs) {
    List<RefEntity> children = refElement.getChildren();
    RefElement[] refElements = children.toArray(new RefElement[0]);
    for (RefElement refChild : refElements) {
      removeRefElement(refChild, deletedRefs);
    }

    ((RefManagerImpl)refElement.getRefManager()).removeReference(refElement);
    ((RefElementImpl)refElement).referenceRemoved();
    if (!deletedRefs.contains(refElement)) {
      deletedRefs.add(refElement);
    }
    else {
      LOG.error("deleted second time");
    }
  }

  public boolean isValidPointForReference() {
    return myIsInProcess || myOfflineView || ApplicationManager.getApplication().isUnitTestMode();
  }
}
