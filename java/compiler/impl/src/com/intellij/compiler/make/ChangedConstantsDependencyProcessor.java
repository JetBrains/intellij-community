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

/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.make;

import com.intellij.compiler.classParsing.FieldInfo;
import com.intellij.compiler.impl.ExitException;
import com.intellij.compiler.impl.ExitStatus;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ChangedConstantsDependencyProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.ChangedConstantsDependencyProcessor");
  private final Project myProject;
  private final CachingSearcher mySearcher;
  private final DependencyCache myDependencyCache;
  private final int myQName;
  private final CompileContext myContext;
  private final FieldChangeInfo[] myChangedFields;
  private final FieldChangeInfo[] myRemovedFields;
  private final int MAX_CONSTANT_SEARCHES = Registry.intValue("compiler.max.static.constants.searches");


  public ChangedConstantsDependencyProcessor(Project project,
                                             CachingSearcher searcher,
                                             DependencyCache dependencyCache,
                                             int qName, CompileContext context, FieldChangeInfo[] changedFields,
                                             FieldChangeInfo[] removedFields) {
    myProject = project;
    mySearcher = searcher;
    myDependencyCache = dependencyCache;
    myQName = qName;
    myContext = context;
    myChangedFields = changedFields;
    myRemovedFields = removedFields;
  }

  public void run() throws CacheCorruptedException, ExitException {
    final Ref<CacheCorruptedException> _ex = new Ref<CacheCorruptedException>();
    final Ref<ExitException> exitException = new Ref<ExitException>(null);
    DumbService.getInstance(myProject).runReadActionInSmartMode(new Runnable() {
      public void run() {
        try {
          final String qName = myDependencyCache.resolve(myQName);
          PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(qName.replace('$', '.'), GlobalSearchScope.allScope(myProject));
          for (PsiClass aClass : classes) {
            PsiField[] psiFields = aClass.getFields();
            for (PsiField psiField : psiFields) {
              final FieldChangeInfo changeInfo = findChangeInfo(psiField);
              if (changeInfo != null) { // this field has been changed
                processFieldChanged(psiField, aClass, changeInfo.isAccessibilityChange);
              }
            }
            for (FieldChangeInfo removedField : myRemovedFields) {
              processFieldRemoved(removedField.fieldInfo, aClass);
            }
          }
        }
        catch (CacheCorruptedException e) {
          _ex.set(e);
        }
        catch (ExitException e) {
          exitException.set(e);
        }
        catch (ProcessCanceledException e) {
          // supressed deliberately
        }
      }
    });
    if (_ex.get() != null) {
      throw _ex.get();
    }
    if (exitException.get() != null) {
      throw exitException.get();
    }
  }

  private void processFieldRemoved(FieldInfo info, final PsiClass aClass) throws CacheCorruptedException {
    if (info.isPrivate()) {
      return; // optimization: don't need to search, cause may be used only in this class
    }
    SearchScope searchScope = GlobalSearchScope.projectScope(myProject);
    if (info.isPackageLocal()) {
      final PsiFile containingFile = aClass.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        final String packageName = ((PsiJavaFile)containingFile).getPackageName();
        final PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
        if (aPackage != null) {
          searchScope = PackageScope.packageScope(aPackage, false);
          searchScope = searchScope.intersectWith(aClass.getUseScope());
        }
      }
    }
    final PsiSearchHelper psiSearchHelper = PsiSearchHelper.SERVICE.getInstance(myProject);

    final Ref<CacheCorruptedException> exRef = new Ref<CacheCorruptedException>(null);
    processIdentifiers(psiSearchHelper, new PsiElementProcessor<PsiIdentifier>() {
      @Override
      public synchronized boolean execute(@NotNull PsiIdentifier identifier) {
        try {
          final PsiElement parent = identifier.getParent();
          if (parent instanceof PsiReferenceExpression) {
            final PsiClass ownerClass = getOwnerClass(parent);
            if (ownerClass != null && !ownerClass.equals(aClass)) {
              final String _qName = ownerClass.getQualifiedName();
              if (_qName != null) {
                int qualifiedName = myDependencyCache.getSymbolTable().getId(_qName);
                // should force marking of the class no matter was it compiled or not
                // This will ensure the class was recompiled _after_ all the constants get their new values
                if (myDependencyCache.markClass(qualifiedName, true)) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Mark dependent class " + myDependencyCache.resolve(qualifiedName) + "; reason: some constants were removed from " + myDependencyCache.resolve(myQName));
                  }
                }
              }
              else {
                LOG.warn("Class with null qualified name was not expected here: " + ownerClass);
              }
            }
          }
          return true;
        }
        catch (CacheCorruptedException e) {
          exRef.set(e);
          return false;
        }
      }
    }, myDependencyCache.resolve(info.getName()), searchScope, UsageSearchContext.IN_CODE);
    
    final CacheCorruptedException cacheCorruptedException = exRef.get();
    if (cacheCorruptedException != null) {
      throw cacheCorruptedException;
    }
  }

  private static boolean processIdentifiers(PsiSearchHelper helper,
                                            @NotNull final PsiElementProcessor<PsiIdentifier> processor,
                                            @NotNull final String identifier,
                                            @NotNull SearchScope searchScope,
                                            short searchContext) {
    TextOccurenceProcessor processor1 = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        return !(element instanceof PsiIdentifier) || processor.execute((PsiIdentifier)element);
      }
    };
    return helper.processElementsWithWord(processor1, searchScope, identifier, searchContext, true);
  }

  private void processFieldChanged(PsiField field, PsiClass aClass, final boolean isAccessibilityChange)
    throws CacheCorruptedException, ExitException {
    if (!isAccessibilityChange && field.hasModifierProperty(PsiModifier.PRIVATE)) {
      return; // optimization: don't need to search, cause may be used only in this class
    }
    Set<PsiElement> usages = new HashSet<PsiElement>();
    addUsages(field, usages, isAccessibilityChange);
    if (LOG.isDebugEnabled()) {
      LOG.debug("++++++++++++++++++++++++++++++++++++++++++++++++");
      LOG.debug("Processing changed field: " + aClass.getQualifiedName() + "." + field.getName());
    }
    for (final PsiElement usage : usages) {
      PsiClass ownerClass = getOwnerClass(usage);
      if (LOG.isDebugEnabled()) {
        if (ownerClass != null) {
          LOG.debug("Usage " + usage + " found in class: " + ownerClass.getQualifiedName());
        }
        else {
          LOG.debug("Usage " + usage + " found in class: null");
        }
      }
      if (ownerClass != null && !ownerClass.equals(aClass)) {
        int qualifiedName = myDependencyCache.getSymbolTable().getId(ownerClass.getQualifiedName());
        // should force marking of the class no matter was it compiled or not
        // This will ensure the class was recompiled _after_ all the constants get their new values
        if (LOG.isDebugEnabled()) {
          LOG.debug("Marking class id = [" + qualifiedName + "], name=[" + myDependencyCache.resolve(qualifiedName) + "]");
        }
        if (myDependencyCache.markClass(qualifiedName, true)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Marked dependent class " + myDependencyCache.resolve(qualifiedName) + "; reason: constants changed in " +
                        myDependencyCache.resolve(myQName));
          }
        }
      }
      else if (ownerClass == null) {
        final PsiFile containingFile = usage.getContainingFile();
        if (containingFile != null) {
          final VirtualFile file = containingFile.getVirtualFile();
          if (file != null) {
            myDependencyCache.markFile(file);
          }
        }
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("+++++++++++++++++++++++++++++++++++++++++++++++");
    }
  }

  private void addUsages(PsiField psiField, Collection<PsiElement> usages, final boolean ignoreAccessScope) throws ExitException {
    final int count = getConstantSearchesCount();
    if (count > MAX_CONSTANT_SEARCHES) {
      myContext.requestRebuildNextTime("Too many changed compile-time constants, project rebuild scheduled");
      throw new ExitException(ExitStatus.CANCELLED);
    }
    Collection<PsiReference> references = mySearcher.findReferences(psiField, ignoreAccessScope)/*doFindReferences(searchHelper, psiField)*/;

    incConstantSearchesCount();

    for (final PsiReference ref : references) {
      if (!(ref instanceof PsiReferenceExpression)) {
        continue;
      }
      PsiElement e = ref.getElement();
      usages.add(e);
      PsiField ownerField = getOwnerField(e);
      if (ownerField != null) {
        if (ownerField.hasModifierProperty(PsiModifier.FINAL)) {
          PsiExpression initializer = ownerField.getInitializer();
          if (initializer != null && PsiUtil.isConstantExpression(initializer)) {
            // if the field depends on the compile-time-constant expression and is itself final
            addUsages(ownerField, usages, ignoreAccessScope);
          }
        }
      }
    }
  }

  /*
  private PsiReference[] doFindReferences(final PsiSearchHelper searchHelper, final PsiField psiField) {
    final ProgressManager progressManager = ProgressManager.getInstance();
    final ProgressIndicator currentProgress = progressManager.getProgressIndicator();
    final PsiReference[][] references = new PsiReference[][] {null};
    progressManager.runProcess(new Runnable() {
      public void run() {
        references[0] = searchHelper.findReferences(psiField, GlobalSearchScope.projectScope(myProject), false);
        if (ENABLE_TRACING) {
          System.out.println("Finding referencers for " + psiField);
        }
      }
    }, new NonCancellableProgressAdapter(currentProgress));
    return references[0];
  }
  */

  @Nullable
  private static PsiField getOwnerField(PsiElement element) {
    while (!(element instanceof PsiFile)) {
      if (element instanceof PsiClass) {
        break;
      }
      if (element instanceof PsiField) { // top-level class
        return (PsiField)element;
      }
      element = element.getParent();
    }
    return null;
  }

  @Nullable
  private FieldChangeInfo findChangeInfo(PsiField field) throws CacheCorruptedException {
    String name = field.getName();
    for (final FieldChangeInfo changeInfo : myChangedFields) {
      if (name.equals(myDependencyCache.resolve(changeInfo.fieldInfo.getName()))) {
        return changeInfo;
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass getOwnerClass(PsiElement element) {
    while (!(element instanceof PsiFile)) {
      if (element instanceof PsiClass && element.getParent() instanceof PsiJavaFile) { // top-level class
        final PsiClass psiClass = (PsiClass)element;
        if (JspPsiUtil.isInJspFile(psiClass)) {
          return null;
        }
        final PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile == null) {
          return null;
        }
        return StdLanguages.JAVA.equals(containingFile.getLanguage())? psiClass : null;
      }
      element = element.getParent();
    }
    return null;
  }

  public static class FieldChangeInfo {
    final FieldInfo fieldInfo;
    final boolean isAccessibilityChange;

    public FieldChangeInfo(final FieldInfo fieldId) {
      this(fieldId, false);
    }

    public FieldChangeInfo(final FieldInfo fieldInfo, final boolean accessibilityChange) {
      this.fieldInfo = fieldInfo;
      isAccessibilityChange = accessibilityChange;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final FieldChangeInfo fieldChangeInfo = (FieldChangeInfo)o;

      if (isAccessibilityChange != fieldChangeInfo.isAccessibilityChange) return false;
      if (!fieldInfo.equals(fieldChangeInfo.fieldInfo)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = fieldInfo.hashCode();
      result = 29 * result + (isAccessibilityChange ? 1 : 0);
      return result;
    }
  }

  private static final Key<Integer> CONSTANTS_COUNTER = Key.create("_constant_searches_counter_");

  private int getConstantSearchesCount() {
    return getConstantSearchesCount(myContext);
  }

  public static int getConstantSearchesCount(CompileContext context) {
    final Integer value = CONSTANTS_COUNTER.get(context);
    return value != null? value.intValue() : 0;
  }

  private void incConstantSearchesCount() {
    CONSTANTS_COUNTER.set(myContext, getConstantSearchesCount() + 1);
  }
}
