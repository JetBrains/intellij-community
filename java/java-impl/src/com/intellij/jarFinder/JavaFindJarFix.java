package com.intellij.jarFinder;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImportStaticStatementImpl;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class JavaFindJarFix extends FindJarFix<PsiQualifiedReferenceElement> {
  public JavaFindJarFix(PsiQualifiedReferenceElement ref) {
    super(ref);
  }

  @Override
  protected Collection<String> getFqns(@NotNull PsiQualifiedReferenceElement ref) {
    final PsiImportStatementBase importStatement = PsiTreeUtil.getParentOfType(ref.getElement(), PsiImportStatementBase.class);

    //from static imports
    if (importStatement != null) {
      if (importStatement instanceof PsiImportStatement) {
        final String importFQN = ((PsiImportStatement)importStatement).getQualifiedName();
        if (importFQN != null && !importFQN.endsWith("*")) {
          return Collections.singleton(importFQN);
        }
      }
      else if (importStatement instanceof PsiImportStaticStatementImpl) {
        final PsiJavaCodeReferenceElement classRef = ((PsiImportStaticStatementImpl)importStatement).getClassReference();
        if (classRef != null) {
          final String importFQN = classRef.getQualifiedName();
          if (importFQN != null) {
            return Collections.singleton(importFQN);
          }
        }
      }
      return Collections.emptyList();
    }

    final PsiElement qualifier = ref.getQualifier();
    if (qualifier instanceof PsiQualifiedReference) {
      //PsiQualifiedReference r = (PsiQualifiedReference)qualifier;
      //TODO[kb] get fqn from expressions like org.unresolvedPackage.MyClass.staticMethodCall(...);
      return Collections.emptyList();
    }
    final String className = ref.getReferenceName();
    PsiFile file = ref.getContainingFile().getOriginalFile();
    if (className != null && file instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList != null) {
        final PsiImportStatementBase statement = importList.findSingleImportStatement(className);
        if (statement instanceof PsiImportStatement) {
          final String importFQN = ((PsiImportStatement)statement).getQualifiedName();
          if (importFQN != null) {
            return Collections.singleton(importFQN);
          }
        }
        else {
          List<String> res = new ArrayList<>();
          // iterate through *
          for (PsiImportStatementBase imp : importList.getAllImportStatements()) {
            if (imp.isOnDemand() && imp instanceof PsiImportStatement) {
              res.add(((PsiImportStatement)imp).getQualifiedName() + "." + className);
            }
          }

          return res;
        }
      }
    }
    return Collections.emptyList();
  }
}
