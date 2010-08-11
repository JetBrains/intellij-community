/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RefManagerImpl extends RefManager {

  private int myLastUsedMask = 256 * 256 * 256 * 4;

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.reference.RefManager");

  private final Project myProject;
  private AnalysisScope myScope;
  private RefProject myRefProject;
  private THashMap<PsiAnchor, RefElement> myRefTable;

  private THashMap<Module, RefModule> myModules;
  private final ProjectIterator myProjectIterator;
  private boolean myDeclarationsFound;
  private final PsiManager myPsiManager;

  private boolean myIsInProcess = false;

  private final List<RefGraphAnnotator> myGraphAnnotators = new ArrayList<RefGraphAnnotator>();
  private GlobalInspectionContextImpl myContext;

  private final Map<Key, RefManagerExtension> myExtensions = new HashMap<Key, RefManagerExtension>();
  private final HashMap<Language, RefManagerExtension> myLanguageExtensions = new HashMap<Language, RefManagerExtension>();

  private final JBReentrantReadWriteLock myLock = LockFactory.createReadWriteLock();

  public RefManagerImpl(Project project, AnalysisScope scope, GlobalInspectionContextImpl context) {
    myDeclarationsFound = false;
    myProject = project;
    myScope = scope;
    myContext = context;
    myPsiManager = PsiManager.getInstance(project);
    myRefProject = new RefProjectImpl(this);
    myRefTable = new THashMap<PsiAnchor, RefElement>();
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
      final THashMap<PsiAnchor, RefElement> refTable = getRefTable();
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

    myGraphAnnotators.clear();
    for (RefManagerExtension extension : myExtensions.values()) {
      extension.cleanup();
    }
  }

  public AnalysisScope getScope() {
    return myScope;
  }


  public void fireNodeInitialized(RefElement refElement) {
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

  public void fireBuildReferences(RefElement refElement) {
    for (RefGraphAnnotator annotator : myGraphAnnotators) {
      annotator.onReferencesBuild(refElement);
    }
  }

  public void registerGraphAnnotator(RefGraphAnnotator annotator) {
    myGraphAnnotators.add(annotator);
  }

  public int getLastUsedMask() {
    myLastUsedMask *= 2;
    return myLastUsedMask;
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
    } else if (ref instanceof RefDirectory) {
      return SmartRefElementPointer.DIR;
    }
    return null;
  }

  public RefEntity getRefinedElement(RefEntity ref) {
    for (RefManagerExtension extension : myExtensions.values()) {
      ref = extension.getRefinedElement(ref);
    }
    return ref;
  }

  public Element export(RefEntity refEntity, final Element element, final int actualLine) {
    refEntity = getRefinedElement(refEntity);

    Element problem = new Element("problem");

    if (refEntity instanceof RefElement) {
      final RefElement refElement = (RefElement)refEntity;
      PsiElement psiElement = refElement.getElement();
      PsiFile psiFile = psiElement.getContainingFile();

      Element fileElement = new Element("file");
      Element lineElement = new Element("line");
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      fileElement.addContent(virtualFile.getUrl());

      if (actualLine == -1) {
        final Document document = PsiDocumentManager.getInstance(refElement.getRefManager().getProject()).getDocument(psiFile);
        LOG.assertTrue(document != null);
        lineElement.addContent(String.valueOf(document.getLineNumber(psiElement.getTextOffset()) + 1));
      }
      else {
        lineElement.addContent(String.valueOf(actualLine));
      }

      problem.addContent(fileElement);
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

    for (RefManagerExtension extension : myExtensions.values()) {
      extension.export(refEntity, problem);
    }

    new SmartRefElementPointerImpl(refEntity, true).writeExternal(problem);
    element.addContent(problem);
    return problem;
  }

  @Nullable
  public String getGroupName(final RefElement entity) {
    for (RefManagerExtension extension : myExtensions.values()) {
      final String groupName = extension.getGroupName(entity);
      if (groupName != null) return groupName;
    }
    return null;
  }

  private static void appendModule(final Element problem, final RefModule refModule) {
    if (refModule != null) {
      Element moduleElement = new Element("module");
      moduleElement.addContent(refModule.getName());
      problem.addContent(moduleElement);
    }
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

  public THashMap<PsiAnchor, RefElement> getRefTable() {
    return myRefTable;
  }

  @Override
  public PsiManager getPsiManager() {
    return myPsiManager;
  }

  public void removeReference(RefElement refElem) {
    myLock.writeLock().lock();
    try {
      final THashMap<PsiAnchor, RefElement> refTable = getRefTable();
      final PsiElement element = refElem.getElement();
      final RefManagerExtension extension = element != null ? getExtension(element.getLanguage()) : null;
      if (extension != null) {
        extension.removeReference(refElem);
      }

      if (element != null && refTable.remove(ApplicationManager.getApplication().runReadAction(
          new Computable<PsiAnchor>() {
            public PsiAnchor compute() {
              return PsiAnchor.create(element);
            }
          }
      )) != null) return;

      //PsiElement may have been invalidated and new one returned by getElement() is different so we need to do this stuff.
      for (PsiAnchor psiElement : refTable.keySet()) {
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
    ExtensionPoint<RefGraphAnnotator> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.INSPECTIONS_GRAPH_ANNOTATOR);
    final RefGraphAnnotator[] graphAnnotators = point.getExtensions();
    for (RefGraphAnnotator annotator : graphAnnotators) {
      registerGraphAnnotator(annotator);
    }
    for (RefGraphAnnotator graphAnnotator : myGraphAnnotators) {
      if (graphAnnotator instanceof RefGraphAnnotatorEx) {
        ((RefGraphAnnotatorEx)graphAnnotator).initialize(this);
      }
    }
  }

  private class ProjectIterator extends PsiElementVisitor {
    @Override
    public void visitElement(PsiElement element) {
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
        myContext
          .incrementJobDoneAmount(GlobalInspectionContextImpl.BUILD_GRAPH, ProjectUtil.calcRelativeToProjectPath(virtualFile, myProject));
      }
      final FileViewProvider viewProvider = file.getViewProvider();
      final Set<Language> relevantLanguages = viewProvider.getLanguages();
      for (Language language : relevantLanguages) {
        visitElement(viewProvider.getPsi(language));
      }
      myPsiManager.dropResolveCaches();
    }
  }

  @Nullable
  public RefElement getReference(final PsiElement elem) {
    if (elem != null && (elem instanceof PsiDirectory || belongsToScope(elem))) {
      if (!elem.isValid()) return null;

      RefElement ref = getFromRefTable(elem);
      if (ref == null) {
        if (!isValidPointForReference()) {
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
              return new RefDirectoryImpl((PsiDirectory)elem, RefManagerImpl.this);
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
    }
    else if (SmartRefElementPointer.MODULE.equals(type)) {
      return RefModuleImpl.moduleFromName(this, fqName);
    }
    else if (SmartRefElementPointer.PROJECT.equals(type)) {
      return getRefProject();
    } else if (SmartRefElementPointer.DIR.equals(type)) {
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(PathMacroManager.getInstance(getProject()).expandPath(fqName));
      if (vFile != null) {
        final PsiDirectory dir = PsiManager.getInstance(getProject()).findDirectory(vFile);
        return getReference(dir);
      }
    }
    return null;
  }

  protected RefElement getFromRefTable(final PsiElement element) {
    myLock.readLock().lock();
    try {
      return getRefTable().get(ApplicationManager.getApplication().runReadAction(
          new Computable<PsiAnchor>() {
            public PsiAnchor compute() {
              return PsiAnchor.create(element);
            }
          }
      ));
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  protected void putToRefTable(final PsiElement element, final RefElement ref) {
    myLock.writeLock().lock();
    try {
      getRefTable().put(ApplicationManager.getApplication().runReadAction(
          new Computable<PsiAnchor>() {
            public PsiAnchor compute() {
              return PsiAnchor.create(element);
            }
          }
      ), ref);
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public RefModule getRefModule(Module module) {
    if (module == null) {
      return null;
    }
    if (myModules == null) {
      myModules = new THashMap<Module, RefModule>();
    }
    RefModule refModule = myModules.get(module);
    if (refModule == null) {
      refModule = new RefModuleImpl(module, this);
      myModules.put(module, refModule);
    }
    return refModule;
  }

  public boolean belongsToScope(final PsiElement psiElement) {
    if (psiElement == null || !psiElement.isValid()) return false;
    if (psiElement instanceof PsiCompiledElement) return false;
    final PsiFile containingFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      public PsiFile compute() {
        return psiElement.getContainingFile();
      }
    });
    if (containingFile == null) {
      return false;
    }
    for (RefManagerExtension extension : myExtensions.values()) {
      if (!extension.belongsToScope(psiElement)) return false;
    }
    final Boolean inProject = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return psiElement.getManager().isInProject(psiElement);
      }
    });
    return inProject.booleanValue() && (getScope() == null || getScope().contains(psiElement));
  }

  public String getQualifiedName(RefEntity refEntity) {

    if (refEntity == null || refEntity instanceof RefElementImpl && !refEntity.isValid()) {
      return InspectionsBundle.message("inspection.reference.invalid");
    }

    return refEntity.getQualifiedName();
  }

  public void removeRefElement(RefElement refElement, List<RefElement> deletedRefs) {
    List<RefEntity> children = refElement.getChildren();
    if (children != null) {
      RefElement[] refElements = children.toArray(new RefElement[children.size()]);
      for (RefElement refChild : refElements) {
        removeRefElement(refChild, deletedRefs);
      }
    }

    ((RefManagerImpl)refElement.getRefManager()).removeReference(refElement);
    ((RefElementImpl)refElement).referenceRemoved();
    if (!deletedRefs.contains(refElement)) deletedRefs.add(refElement);
  }

  protected boolean isValidPointForReference() {
    return myIsInProcess || ApplicationManager.getApplication().isUnitTestMode();
  }
}
