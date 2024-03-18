// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

public final class JavaCopyPasteReferenceProcessor extends CopyPasteReferenceProcessor<PsiJavaCodeReferenceElement> {
  private static final Logger LOG = Logger.getInstance(JavaCopyPasteReferenceProcessor.class);

  @Override
  protected void addReferenceData(PsiFile file, int startOffset, PsiElement element, ArrayList<ReferenceData> to) {
    if (element instanceof PsiJavaCodeReferenceElement) {
      if (!((PsiJavaCodeReferenceElement)element).isQualified()) {
        final JavaResolveResult resolveResult = ((PsiJavaCodeReferenceElement)element).advancedResolve(false);
        final PsiElement refElement = resolveResult.getElement();
        if (refElement != null) {

          if (refElement instanceof PsiClass) {
            final String qName = ((PsiClass)refElement).getQualifiedName();
            if (qName != null) {
              addReferenceData(element, to, startOffset, qName, null);
            }
          }
          else if (resolveResult.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
            final String classQName = ((PsiMember)refElement).getContainingClass().getQualifiedName();
            final String name = ((PsiNamedElement)refElement).getName();
            if (classQName != null && name != null) {
              addReferenceData(element, to, startOffset, classQName, name);
            }
          }
        }
      }
    }
  }

  @Override
  protected void removeImports(@NotNull PsiFile file, @NotNull Set<String> imports) {
    removeImports((PsiJavaFile)file, imports);
  }

  /**
   * Remove imports on {@code imports} (including static imports in format Class_Name.Member_Name)
   * To ensure that on-demand import expands when one of the import inside was deleted, let's do optimize imports.
   *
   * This may change some unrelated imports
   */
  public static void removeImports(PsiJavaFile javaFile, Set<String> imports) {
    PsiImportList importList = new ImportHelper(JavaCodeStyleSettings.getInstance(javaFile))
      .prepareOptimizeImportsResult(javaFile, anImport -> !imports.contains(anImport.name()));
    if (importList != null) {
      Objects.requireNonNull(javaFile.getImportList()).replace(importList);
    }
  }


  @Override
  protected PsiJavaCodeReferenceElement @NotNull [] findReferencesToRestore(@NotNull PsiFile file,
                                                                            @NotNull RangeMarker bounds,
                                                                            ReferenceData @NotNull [] referenceData) {
    PsiManager manager = file.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    PsiResolveHelper helper = facade.getResolveHelper();
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[referenceData.length];
    for (int i = 0; i < referenceData.length; i++) {
      ReferenceData data = referenceData[i];

      PsiClass refClass = facade.findClass(data.qClassName, file.getResolveScope());
      if (refClass == null) continue;

      int startOffset = data.startOffset + bounds.getStartOffset();
      int endOffset = data.endOffset + bounds.getStartOffset();
      PsiElement element = file.findElementAt(startOffset);

      if (element instanceof PsiIdentifier && element.getParent() instanceof PsiJavaCodeReferenceElement reference) {
        TextRange range = reference.getTextRange();
        if (range.getStartOffset() == startOffset && range.getEndOffset() == endOffset) {
          if (data.staticMemberName == null) {
            PsiClass refClass1 = helper.resolveReferencedClass(reference.getText(), reference);
            if (refClass1 == null || !manager.areElementsEquivalent(refClass, refClass1)) {
              refs[i] = reference;
            }
          }
          else {
            if (reference instanceof PsiReferenceExpression) {
              PsiElement referent = resolveReferenceIgnoreOverriding(reference);

              if (!(referent instanceof PsiNamedElement)
                  || !data.staticMemberName.equals(((PsiNamedElement)referent).getName())
                  || !(referent instanceof PsiMember)
                  || ((PsiMember)referent).getContainingClass() == null
                  || !data.qClassName.equals(((PsiMember)referent).getContainingClass().getQualifiedName())) {
                refs[i] = reference;
              }
            }
          }
        }
      }
    }

    return refs;
  }

  @Override
  protected void restoreReferences(ReferenceData @NotNull [] referenceData,
                                   PsiJavaCodeReferenceElement @NotNull [] refs,
                                   @NotNull Set<? super String> imported) {
    for (int i = 0; i < refs.length; i++) {
      PsiJavaCodeReferenceElement reference = refs[i];
      if (reference == null || !reference.isValid()) continue;
      try {
        PsiManager manager = reference.getManager();
        ReferenceData refData = referenceData[i];
        ProgressManager.progress2(refData.qClassName);
        PsiClass refClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(refData.qClassName, reference.getResolveScope());
        if (refClass != null) {
          if (refData.staticMemberName == null) {
            if (reference instanceof PsiJavaCodeReferenceElementImpl &&
                ((PsiJavaCodeReferenceElementImpl)reference).getKindEnum(reference.getContainingFile()) ==
                PsiJavaCodeReferenceElementImpl.Kind.PACKAGE_NAME_KIND) {
              // Trying to paste class reference into e.g. package statement
              continue;
            }
            reference.bindToElement(refClass);
            imported.add(refData.qClassName);
          }
          else {
            LOG.assertTrue(reference instanceof PsiReferenceExpression);
            ((PsiReferenceExpression)reference).bindToElementViaStaticImport(refClass);
            imported.add(StringUtil.getQualifiedName(refData.qClassName, refData.staticMemberName));
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}
