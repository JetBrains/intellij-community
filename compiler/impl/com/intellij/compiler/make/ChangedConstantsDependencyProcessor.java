/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.make;

import com.intellij.compiler.classParsing.FieldInfo;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ChangedConstantsDependencyProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.ChangedConstantsDependencyProcessor");
  private final Project myProject;
  private final CachingSearcher mySearcher;
  private DependencyCache myDependencyCache;
  private final int myQName;
  private final FieldChangeInfo[] myChangedFields;
  private final FieldChangeInfo[] myRemovedFields;


  public ChangedConstantsDependencyProcessor(Project project, CachingSearcher searcher, DependencyCache dependencyCache, int qName, FieldChangeInfo[] changedFields, FieldChangeInfo[] removedFields) {
    myProject = project;
    mySearcher = searcher;
    myDependencyCache = dependencyCache;
    myQName = qName;
    myChangedFields = changedFields;
    myRemovedFields = removedFields;
  }

  public void run() throws CacheCorruptedException {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final CacheCorruptedException[] _ex = new CacheCorruptedException[] {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          final String qName = myDependencyCache.resolve(myQName);
          PsiClass[] classes = psiManager.findClasses(qName.replace('$', '.'), GlobalSearchScope.allScope(myProject));
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
         _ex[0] = e;
        }
        catch (ProcessCanceledException e) {
          // supressed deliberately
        }
      }
    });
    if (_ex[0] != null) {
      throw _ex[0];
    }
  }

  private void processFieldRemoved(FieldInfo info, PsiClass aClass) throws CacheCorruptedException {
    if (info.isPrivate()) {
      return; // optimization: don't need to search, cause may be used only in this class
    }
    SearchScope searchScope = GlobalSearchScope.projectScope(myProject);
    if (info.isPackageLocal()) {
      final PsiFile containingFile = aClass.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        final String packageName = ((PsiJavaFile)containingFile).getPackageName();
        final PsiPackage aPackage = PsiManager.getInstance(myProject).findPackage(packageName);
        if (aPackage != null) {
          searchScope = GlobalSearchScope.packageScope(aPackage, false);
          searchScope = searchScope.intersectWith(aClass.getUseScope());
        }
      }
    }
    final PsiSearchHelper psiSearchHelper = PsiManager.getInstance(myProject).getSearchHelper();
    PsiIdentifier[] identifiers = psiSearchHelper.findIdentifiers(myDependencyCache.resolve(info.getName()), searchScope, UsageSearchContext.IN_CODE);
    for (PsiIdentifier identifier : identifiers) {
      PsiElement parent = identifier.getParent();
      if (parent instanceof PsiReferenceExpression) {
        PsiReferenceExpression refExpr = (PsiReferenceExpression)parent;
        PsiReference reference = refExpr.getReference();
        if (reference.resolve() == null) {
          PsiClass ownerClass = getOwnerClass(refExpr);
          if (ownerClass != null && !ownerClass.equals(aClass)) {
            int qualifiedName = myDependencyCache.getSymbolTable().getId(ownerClass.getQualifiedName());
            // should force marking of the class no matter was it compiled or not
            // This will ensure the class was recompiled _after_ all the constants get their new values
            if (myDependencyCache.markClass(qualifiedName, true)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mark dependent class " + myDependencyCache.resolve(qualifiedName) +
                          "; reason: some constants were removed from " + myDependencyCache.resolve(myQName));
              }
            }
          }
        }
      }
    }
  }

  private void processFieldChanged(PsiField field, PsiClass aClass, final boolean isAccessibilityChange) throws CacheCorruptedException {
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
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("+++++++++++++++++++++++++++++++++++++++++++++++");
    }
  }

  private void addUsages(PsiField psiField, Collection<PsiElement> usages, final boolean ignoreAccessScope) {
    Collection<PsiReference> references = mySearcher.findReferences(psiField, ignoreAccessScope)/*doFindReferences(searchHelper, psiField)*/;
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

  private PsiField getOwnerField(PsiElement element) {
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
        if (PsiUtil.isInJspFile(psiClass)) {
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

}
