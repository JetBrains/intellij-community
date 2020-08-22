// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.TemplateLookupSelectionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class JavaTemplateUtil {
  private static final Logger LOG = Logger.getInstance(JavaTemplateUtil.class);

  private JavaTemplateUtil() {
  }

  public static void updateTypeBindings(Object item, PsiFile file, final Document document, final int segmentStart, final int segmentEnd) {
    updateTypeBindings(item, file, document, segmentStart, segmentEnd, false);
  }

  public static void updateTypeBindings(Object item,
                                        PsiFile file,
                                        final Document document,
                                        final int segmentStart,
                                        final int segmentEnd,
                                        boolean noImport) {
    final Project project = file.getProject();
    List<PsiClass> classes = new ArrayList<>();
    if (item instanceof PsiClass) {
      classes.add((PsiClass)item);
    }
    else if (item instanceof PsiClassType) {
      PsiTypesUtil.TypeParameterSearcher searcher = new PsiTypesUtil.TypeParameterSearcher();
      ((PsiClassType)item).accept(searcher);
      classes.addAll(searcher.getTypeParameters());
    }

    if (!classes.isEmpty()) {
      for (PsiClass aClass : classes) {
        if (aClass instanceof PsiTypeParameter) {
          PsiElement element = file.findElementAt(segmentStart);
          PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
          if (method != null) {
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
              PsiTypeParameterListOwner owner = ((PsiTypeParameter)aClass).getOwner();
              if (PsiTreeUtil.isAncestor(owner, method, false)) {
                continue;
              }
            }

            PsiTypeParameterList paramList = method.getTypeParameterList();
            PsiTypeParameter[] params = paramList != null ? paramList.getTypeParameters() : PsiTypeParameter.EMPTY_ARRAY;
            for (PsiTypeParameter param : params) {
              if (param.getName().equals(aClass.getName())) return;
            }
            try {
              if (paramList == null) {
                final PsiTypeParameterList newList =
                  JVMElementFactories.getFactory(method.getLanguage(), project).createTypeParameterList();
                paramList = (PsiTypeParameterList)method.addAfter(newList, method.getModifierList());
              }
              paramList.add(aClass.copy());
              PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
        else if (!noImport) {
          addImportForClass(document, aClass, segmentStart, segmentEnd);
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        }
      }
    }
  }

  public static void addImportForClass(final Document document, final PsiClass aClass, final int start, final int end) {
    final Project project = aClass.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (!aClass.isValid() || aClass.getQualifiedName() == null) return;

    JavaPsiFacade manager = JavaPsiFacade.getInstance(project);
    PsiResolveHelper helper = manager.getResolveHelper();

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    CharSequence chars = document.getCharsSequence();

    PsiElement element = file.findElementAt(start);
    String refText = chars.subSequence(start, end).toString();
    PsiClass refClass = helper.resolveReferencedClass(refText, element);
    if (aClass.equals(refClass)) return;

    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      while (parent != null) {
        PsiElement tmp = parent.getParent();
        if (!(tmp instanceof PsiJavaCodeReferenceElement) || tmp.getTextRange().getEndOffset() > end) break;
        parent = tmp;
      }
      if (parent instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement) parent).isQualified()) {
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) parent;
        ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            ref.bindToElement(aClass);
          } catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        });
      }
    }
  }

  public static LookupElement addElementLookupItem(Set<? super LookupElement> items, PsiElement element) {
    final LookupElement item = LookupItemUtil.objectToLookupItem(element);
    items.add(item);
    item.putUserData(TemplateLookupSelectionHandler.KEY_IN_LOOKUP_ITEM, new JavaTemplateLookupSelectionHandler());
    return item;
  }

  public static LookupElement addTypeLookupItem(Set<? super LookupElement> items, PsiType type) {
    final LookupElement item = PsiTypeLookupItem.createLookupItem(type, null);
    items.add(item);
    item.putUserData(TemplateLookupSelectionHandler.KEY_IN_LOOKUP_ITEM, new JavaTemplateLookupSelectionHandler());
    return item;
  }
}
