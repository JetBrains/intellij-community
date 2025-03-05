// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public final class MoveJavaClassHandler implements MoveClassHandler {
  private static final Logger LOG = Logger.getInstance(MoveJavaClassHandler.class);

  @Override
  public void finishMoveClass(@NotNull PsiClass aClass) {
    if (aClass.getContainingFile() instanceof PsiJavaFile) {
      ChangeContextUtil.decodeContextInfo(aClass, null, null);
    }
  }

  @Override
  public void prepareMove(@NotNull PsiClass aClass) {
    if (aClass.getContainingFile() instanceof PsiJavaFile) {
      ChangeContextUtil.encodeContextInfo(aClass, true);
    }
  }

  @Override
  public PsiClass doMoveClass(final @NotNull PsiClass aClass, @NotNull PsiDirectory moveDestination) throws IncorrectOperationException {
    PsiFile file = aClass.getContainingFile();
    final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(moveDestination);

    PsiClass newClass = null;
    if (file instanceof PsiJavaFile) {
      if (!moveDestination.equals(file.getContainingDirectory()) &&
           moveDestination.findFile(file.getName()) != null) {
        // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
        correctSelfReferences(aClass, newPackage);
        final PsiFile newFile = moveDestination.findFile(file.getName());
        LOG.assertTrue(newFile != null);
        newClass = (PsiClass)newFile.add(aClass);
        correctOldClassReferences(newClass, aClass);
        aClass.delete();
      }
      else if (((PsiJavaFile)file).getClasses().length > 1) {
        correctSelfReferences(aClass, newPackage);
        final PsiClass created = JavaDirectoryService.getInstance().createClass(moveDestination, aClass.getName());
        if (aClass.getDocComment() == null) {
          final PsiDocComment createdDocComment = created.getDocComment();
          if (createdDocComment != null) {
            aClass.addAfter(createdDocComment, null);
          }
        }
        newClass = (PsiClass)created.replace(aClass);
        correctOldClassReferences(newClass, aClass);
        aClass.delete();
      }
    }
    return newClass;
  }

  private static void correctOldClassReferences(final PsiClass newClass, final PsiClass oldClass) {
    final Set<PsiImportStatementBase> importsToDelete = new HashSet<>();
    newClass.getContainingFile().accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        if (reference.isValid() && reference.isReferenceTo(oldClass)) {
          final PsiImportStatementBase importStatement = PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class);
          if (importStatement != null) {
            importsToDelete.add(importStatement);
            return;
          }
          try {
            reference.bindToElement(newClass);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        super.visitReferenceElement(reference);
      }
    });
    for (PsiImportStatementBase importStatement : importsToDelete) {
      importStatement.delete();
    }
  }

  private static void correctSelfReferences(final PsiClass aClass, final PsiPackage newContainingPackage) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
    if (aPackage != null) {
      aClass.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
          if (reference.isQualified() && reference.isReferenceTo(aClass)) {
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(aPackage)) {
              try {
                ((PsiJavaCodeReferenceElement)qualifier).bindToElement(newContainingPackage);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }
          super.visitReferenceElement(reference);
        }
      });
    }
  }

  @Override
  public String getName(PsiClass clazz) {
    final PsiFile file = clazz.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    return ((PsiJavaFile)file).getClasses().length > 1 ? clazz.getName() + "." + JavaFileType.INSTANCE.getDefaultExtension() : file.getName();
  }

  @Override
  public void preprocessUsages(Collection<UsageInfo> results) {
  }
}
