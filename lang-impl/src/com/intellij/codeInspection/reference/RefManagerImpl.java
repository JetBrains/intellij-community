/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2001
 * Time: 8:21:36 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.ExtensionPoints;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.ex.EntryPointsManagerImpl;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RefManagerImpl extends RefManager {

  private int myLastUsedMask = 256*256*256*4;

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.reference.RefManager");

  private final Project myProject;
  private AnalysisScope myScope;
  private RefProject myRefProject;
  private THashMap<PsiElement, RefElement> myRefTable;

  private THashMap<Module, RefModule> myModules;
  private final ProjectIterator myProjectIterator;
  private boolean myDeclarationsFound;

  private boolean myIsInProcess = false;

  private List<RefGraphAnnotator> myGraphAnnotators = new ArrayList<RefGraphAnnotator>();
  private GlobalInspectionContextImpl myContext;

  private EntryPointsManager myEntryPointsManager = null;

  private Map<Key, RefManagerExtension> myExtensions = new HashMap<Key, RefManagerExtension>();
  private HashMap<Language, RefManagerExtension> myLanguageExtensions = new HashMap<Language, RefManagerExtension>();

  private JBReentrantReadWriteLock myLock = LockFactory.createReadWriteLock();

  public RefManagerImpl(Project project, AnalysisScope scope, GlobalInspectionContextImpl context) {
    myDeclarationsFound = false;
    myProject = project;
    myScope = scope;
    myContext = context;
    myRefProject = new RefProjectImpl(this);
    myRefTable = new THashMap<PsiElement, RefElement>();
    myProjectIterator = new ProjectIterator();
    for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
      final RefManagerExtension extension = factory.createRefManagerExtension(this);
      myExtensions.put(extension.getID(), extension);
      myLanguageExtensions.put(extension.getLanguage(), extension);
    }
  }

  public void iterate(RefVisitor visitor) {
    myLock.readLock().lock();
    try {
      final Map<PsiElement, RefElement> refTable = getRefTable();
      for (RefElement refElement : refTable.values()) {
        refElement.accept(visitor);
      }
      if (myModules != null) {
        for (RefModule refModule : myModules.values()) {
          refModule.accept(visitor);
        }
      }
      for (RefManagerExtension extension : myExtensions.values()) {
        extension.iterate(visitor);
      }
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  public void cleanup() {
    myScope = null;
    myRefProject = null;
    myRefTable = null;
    myModules = null;
    myContext = null;
    if (myEntryPointsManager != null) {
      myEntryPointsManager.cleanup();
    }
    myGraphAnnotators.clear();
    for (RefManagerExtension extension : myExtensions.values()) {
      extension.cleanup();
    }
  }

  public AnalysisScope getScope() {
    return myScope;
  }


  public void fireNodeInitialized(RefElement refElement){
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onInitialize(refElement);
    }
  }

  public void fireNodeMarkedReferenced(RefElement refWhat,
                                       RefElement refFrom,
                                       boolean referencedFromClassInitializer, final boolean forReading, final boolean forWriting){
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onMarkReferenced(refWhat, refFrom, referencedFromClassInitializer, forReading, forWriting);
    }
  }

  public void fireBuildReferences(RefElement refElement){
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onReferencesBuild(refElement);
    }
  }

  public void registerGraphAnnotator(RefGraphAnnotator annotator){
    myGraphAnnotators.add(annotator);
  }

  public int getLastUsedMask() {
    myLastUsedMask *= 2;
    return myLastUsedMask;
  }

  public EntryPointsManager getEntryPointsManager() {
    if (myEntryPointsManager == null) {
      myEntryPointsManager = new EntryPointsManagerImpl();
      ((EntryPointsManagerImpl)myEntryPointsManager).addAllPersistentEntries(EntryPointsManagerImpl.getInstance(myContext.getProject()));
    }
    return myEntryPointsManager;
  }

  public <T> T getExtension(final Key<T> key) {
    return (T)myExtensions.get(key);
  }


  public String getType(final RefEntity ref) {
    for (RefManagerExtension extension : myExtensions.values()) {
      final String type = extension.getType(ref);
      if (type != null) return type;
    }
    if (ref instanceof RefFile) {
      return SmartRefElementPointer.FILE;
    }
    else if (ref instanceof RefModule) {
      return SmartRefElementPointer.MODULE;
    }
    else if (ref instanceof RefProject) {
      return SmartRefElementPointer.PROJECT;
    }
    return null;
  }

  public RefEntity getRefinedElement(RefEntity ref) {
    for (RefManagerExtension extension : myExtensions.values()) {
      ref = extension.getRefinedElement(ref);
    }
    return ref;
  }

  public void findAllDeclarations() {
    if (!myDeclarationsFound) {
      long before = System.currentTimeMillis();
      getScope().accept(myProjectIterator);
      myDeclarationsFound = true;

      LOG.info("Total duration of processing project usages:" + (System.currentTimeMillis() - before));
    }
  }

  public boolean isDeclarationsFound() {
    return myDeclarationsFound;
  }

  public void inspectionReadActionStarted() {
    myIsInProcess = true;
  }

  public void inspectionReadActionFinished() {
    myIsInProcess = false;
  }


  public boolean isInProcess() {
    return myIsInProcess;
  }

  public Project getProject() {
    return myProject;
  }

  public RefProject getRefProject() {
    return myRefProject;
  }

  public THashMap<PsiElement, RefElement> getRefTable() {
    return myRefTable;
  }

  public void removeReference(RefElement refElem) {
    myLock.writeLock().lock();
    try {
      final Map<PsiElement, RefElement> refTable = getRefTable();
      final PsiElement element = refElem.getElement();
      final RefManagerExtension extension = element != null ? getExtension(element.getLanguage()) : null;
      if (extension != null) {
        extension.removeReference(refElem);
      }

      if (refTable.remove(element) != null) return;

      //PsiElement may have been invalidated and new one returned by getElement() is different so we need to do this stuff.
      for (PsiElement psiElement : refTable.keySet()) {
        if (refTable.get(psiElement) == refElem) {
          refTable.remove(psiElement);
          return;
        }
      }
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public void initializeAnnotators() {
    final Object[] graphAnnotators = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.INSPECTIONS_GRAPH_ANNOTATOR).getExtensions();
    for (Object annotator : graphAnnotators) {
      registerGraphAnnotator((RefGraphAnnotator)annotator);
    }
    for (RefGraphAnnotator graphAnnotator: myGraphAnnotators) {
      if (graphAnnotator instanceof RefGraphAnnotatorEx) {
        ((RefGraphAnnotatorEx)graphAnnotator).initialize(this);
      }
    }
  }

  private class ProjectIterator extends PsiElementVisitor {
    @Override public void visitElement(PsiElement element) {
      final RefManagerExtension extension = getExtension(element.getLanguage());
      if (extension != null) {
        extension.visitElement(element);
      }
      for (PsiElement aChildren : element.getChildren()) {
        aChildren.accept(this);
      }
    }

    @Override
    public void visitFile(PsiFile file) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        myContext.incrementJobDoneAmount(GlobalInspectionContextImpl.BUILD_GRAPH, ProjectUtil.calcRelativeToProjectPath(virtualFile, myProject));
      }
      final FileViewProvider viewProvider = file.getViewProvider();
      final Set<Language> relevantLanguages = viewProvider.getPrimaryLanguages();
      for (Language language : relevantLanguages) {
        visitElement(viewProvider.getPsi(language));
      }
    }
  }

  @Nullable public RefElement getReference(final PsiElement elem) {
    if (elem != null && RefUtil.getInstance().belongsToScope(elem, this)) {
      if (!elem.isValid()) return null;

      RefElement ref = getFromRefTable(elem);
      if (ref == null) {
        if (!isValidPointForReference()){
          //LOG.assertTrue(true, "References may become invalid after process is finished");
          return null;
        }

        final RefElementImpl refElement = ApplicationManager.getApplication().runReadAction(new Computable<RefElementImpl>() {
          @Nullable
          public RefElementImpl compute() {
            final RefManagerExtension extension = getExtension(elem.getLanguage());
            if (extension != null) {
              final RefElement refElement = extension.createRefElement(elem);
              if (refElement != null) return (RefElementImpl)refElement;
            }
            if (elem instanceof PsiFile) {
              return new RefFileImpl((PsiFile)elem, RefManagerImpl.this);
            }
            else if (elem instanceof PsiDirectory) {
              return new RefDirectoryImpl(((PsiDirectory)elem).getName(), elem, RefManagerImpl.this);
            }
            else {
              return null;
            }
          }
        });
        if (refElement == null) return null;

        putToRefTable(elem, refElement);

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            refElement.initialize();
          }
        });

        return refElement;
      }
      return ref;
    }

    return null;
  }

  private RefManagerExtension getExtension(final Language language) {
    return myLanguageExtensions.get(language);
  }

  public
  @Nullable
  RefEntity getReference(final String type, final String fqName) {
    for (RefManagerExtension extension : myExtensions.values()) {
      final RefEntity refEntity = extension.getReference(type, fqName);
      if (refEntity != null) return refEntity;
    }
    if (SmartRefElementPointer.FILE.equals(type)) {
      return RefFileImpl.fileFromExternalName(this, fqName);
    } else if (SmartRefElementPointer.MODULE.equals(type)) {
      return RefModuleImpl.moduleFromName(this, fqName);
    } else if (SmartRefElementPointer.PROJECT.equals(type)) {
      return getRefProject();
    }
    return null;
  }

  protected RefElement getFromRefTable(final PsiElement element) {
    myLock.readLock().lock();
    try {
      return getRefTable().get(element);
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  protected void putToRefTable(final PsiElement element, final RefElement ref) {
    myLock.writeLock().lock();
    try {
      getRefTable().put(element, ref);
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public RefModule getRefModule(Module module) {
    if (module == null){
      return null;
    }
    if (myModules == null){
      myModules = new THashMap<Module, RefModule>();
    }
    RefModule refModule = myModules.get(module);
    if (refModule == null){
      refModule = new RefModuleImpl(module, this);
      myModules.put(module, refModule);
    }
    return refModule;
  }

  protected boolean isValidPointForReference() {
    return myIsInProcess || ApplicationManager.getApplication().isUnitTestMode();
  }
}
