// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.imports;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.ImportsUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceOnDemandImportIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.on.demand.import.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("replace.on.demand.import.intention.name");
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new OnDemandImportPredicate();
  }

  @Override
  protected void invoke(@NotNull PsiElement element) {
    final PsiImportStatementBase importStatementBase = (PsiImportStatementBase)element;
    replaceOnDemand(importStatementBase);
  }

  public static void replaceOnDemand(@NotNull PsiImportStatementBase importStatementBase) {
    final PsiJavaFile javaFile = (PsiJavaFile)importStatementBase.getContainingFile();
    final PsiManager manager = importStatementBase.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (importStatementBase instanceof PsiImportModuleStatement || importStatementBase instanceof PsiImportStatement) {
      final PsiClass[] classes = javaFile.getClasses();
      final ClassCollector visitor = ClassCollector.create(importStatementBase);
      if (visitor == null) return;
      for (PsiClass aClass : classes) {
        aClass.accept(visitor);
      }
      PsiJavaModule psiJavaModule = javaFile.getModuleDeclaration();
      if (psiJavaModule != null) {
        psiJavaModule.accept(visitor);
      }
      ImportUtils.ImplicitImportChecker checker = ImportUtils.createImplicitImportChecker(javaFile);
      final PsiClass[] importedClasses = Arrays.stream(visitor.getImportedClasses())
        .filter(
          cl -> !(importStatementBase instanceof PsiImportModuleStatement) ||
                !checker.isImplicitlyImported(cl.getQualifiedName(), false))
        .sorted(new PsiClassComparator())
        .toArray(PsiClass[]::new);
      createImportStatements(importStatementBase, importedClasses, factory::createImportStatement);
    }
    else if (importStatementBase instanceof PsiImportStaticStatement) {
      PsiClass targetClass = ((PsiImportStaticStatement)importStatementBase).resolveTargetClass();
      if (targetClass != null) {
        String[] members = ImportsUtil.collectReferencesThrough(javaFile,
                                                                importStatementBase.getImportReference(),
                                                                (PsiImportStaticStatement)importStatementBase)
          .stream()
          .map(PsiReference::resolve)
          .filter(resolve -> resolve instanceof PsiMember)
          .map(member -> ((PsiMember)member).getName())
          .distinct()
          .filter(Objects::nonNull)
          .toArray(String[]::new);

        createImportStatements(importStatementBase,
                               members,
                               member -> factory.createImportStaticStatement(targetClass, member));
      }
    }
  }

  private static <T> void createImportStatements(PsiImportStatementBase importStatement,
                                                 T[] importedMembers,
                                                 Function<? super T, ? extends PsiImportStatementBase> function) {
    final PsiElement importList = importStatement.getParent();
    for (T importedMember : importedMembers) {
      importList.add(function.apply(importedMember));
    }
    new CommentTracker().deleteAndRestoreComments(importStatement);
  }

  private static class ClassCollector extends JavaRecursiveElementWalkingVisitor {

    private final @Nullable String importedPackageName;
    private final @Nullable PsiImportModuleStatement importModuleStatement;
    private final Set<PsiClass> importedClasses = new HashSet<>();

    ClassCollector(@NotNull String importedPackageName) {
      this.importedPackageName = importedPackageName;
      this.importModuleStatement = null;
    }

    ClassCollector(@NotNull PsiImportModuleStatement importModuleStatement) {
      this.importedPackageName = null;
      this.importModuleStatement = importModuleStatement;
    }

    @Override
    public void visitReferenceElement(
      @NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass aClass)) {
        return;
      }
      final String qualifiedName = aClass.getQualifiedName();
      final String packageName =
        ClassUtil.extractPackageName(qualifiedName);
      if (importedPackageName != null && importedPackageName.equals(packageName)) {
        importedClasses.add(aClass);
        return;
      }
      if (importModuleStatement != null) {
        PsiPackageAccessibilityStatement aPackage = importModuleStatement.findImportedPackage(packageName);
        if (aPackage != null) {
          importedClasses.add(aClass);
        }
      }
    }

    public PsiClass[] getImportedClasses() {
      return importedClasses.toArray(PsiClass.EMPTY_ARRAY);
    }

    static @Nullable ClassCollector create(@NotNull PsiImportStatementBase statementBase) {
      if (statementBase instanceof PsiImportModuleStatement moduleStatement) {
        return new ClassCollector(moduleStatement);
      }
      if (statementBase instanceof PsiImportStatement importStatement) {
        String qualifiedName = importStatement.getQualifiedName();
        if (qualifiedName == null) return null;
        return new ClassCollector(qualifiedName);
      }
      return null;
    }
  }

  private static final class PsiClassComparator
    implements Comparator<PsiClass> {

    @Override
    public int compare(PsiClass class1, PsiClass class2) {
      final String qualifiedName1 = class1.getQualifiedName();
      final String qualifiedName2 = class2.getQualifiedName();
      if (qualifiedName1 == null) {
        return -1;
      }
      if (qualifiedName2 == null) {
        return 1;
      }
      return qualifiedName1.compareTo(qualifiedName2);
    }
  }
}