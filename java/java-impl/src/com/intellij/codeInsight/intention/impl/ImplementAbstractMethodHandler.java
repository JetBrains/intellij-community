// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.util.PsiClassRenderingInfo;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.PsiElementRenderingInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.*;

public class ImplementAbstractMethodHandler {
  private static final Logger LOG = Logger.getInstance(ImplementAbstractMethodHandler.class);

  private final Project myProject;
  private final Editor myEditor;
  private final PsiMethod myMethod;

  public ImplementAbstractMethodHandler(Project project, Editor editor, PsiMethod method) {
    myProject = project;
    myEditor = editor;
    myMethod = method;
  }

  public void invoke() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Ref<@Nls String> problemDetected = new Ref<>();
    final PsiElement[][] result = new PsiElement[1][];
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
      final PsiClass psiClass = myMethod.getContainingClass();
      if (!psiClass.isValid()) return;
      if (!psiClass.isEnum()) {
        result[0] = getClassImplementations(psiClass, problemDetected);
      }
      else {
        final List<PsiElement> enumConstants = new ArrayList<>();
        for (PsiField field : psiClass.getFields()) {
          if (field instanceof PsiEnumConstant) {
            final PsiEnumConstantInitializer initializingClass = ((PsiEnumConstant)field).getInitializingClass();
            if (initializingClass != null) {
              PsiMethod method = initializingClass.findMethodBySignature(myMethod, true);
              if (method == null || !method.getContainingClass().equals(initializingClass)) {
                enumConstants.add(initializingClass);
              }
            }
            else {
              enumConstants.add(field);
            }
          }
        }
        result[0] = PsiUtilCore.toPsiElementArray(enumConstants);
      }
    }), JavaBundle.message("intention.implement.abstract.method.searching.for.descendants.progress"), true, myProject);

    PsiElement[] elements = result[0];
    if (elements == null) return;

    if (elements.length == 0) {
      Messages.showMessageDialog(myProject,
                                 problemDetected.isNull() ? JavaBundle.message("intention.implement.abstract.method.error.no.classes.message") : problemDetected.get(),
                                 JavaBundle.message("intention.implement.abstract.method.error.no.classes.title"),
                                 Messages.getInformationIcon());
      return;
    }

    if (elements.length == 1) {
      implementInClass(new Object[]{elements[0]});
      return;
    }

    final MyPsiElementListCellRenderer elementListCellRenderer = new MyPsiElementListCellRenderer();
    elementListCellRenderer.sort(elements);
    final IPopupChooserBuilder<PsiElement> builder = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(List.of(elements))
      .setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
      .setRenderer(elementListCellRenderer)
      .setTitle(CodeInsightBundle.message("intention.implement.abstract.method.class.chooser.title")).
                                                                     setItemsChosenCallback((selectedValues) -> {
          if (!selectedValues.isEmpty()) {
            implementInClass(ArrayUtil.toObjectArray(selectedValues));
          }
        })
      .withHintUpdateSupply();
    elementListCellRenderer.installSpeedSearch(builder);
    builder.createPopup().showInBestPositionFor(myEditor);
  }

  public void implementInClass(final Object[] selection) {
    for (Object o : selection) {
      if (!((PsiElement)o).isValid()) return;
    }
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final LinkedHashSet<PsiClass> classes = new LinkedHashSet<>();
      for (final Object o : selection) {
        if (o instanceof PsiEnumConstant) {
          classes.add(ApplicationManager.getApplication().runWriteAction(new Computable<>() {
            @Override
            public PsiClass compute() {
              return ((PsiEnumConstant)o).getOrCreateInitializingClass();
            }
          }));
        }
        else {
          classes.add((PsiClass)o);
        }
      }
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(classes)) return;
      ApplicationManager.getApplication().runWriteAction(() -> {
        for (PsiClass psiClass : classes) {
          try {
            OverrideImplementUtil.overrideOrImplement(psiClass, myMethod);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });
    }, JavaBundle.message("intention.implement.abstract.method.command.name"), null);
  }

  private PsiClass[] getClassImplementations(final PsiClass psiClass, Ref<@Nls String> problemDetected) {
    ArrayList<PsiClass> list = new ArrayList<>();
    Set<String> classNamesWithPotentialImplementations = new LinkedHashSet<>();
    for (PsiClass inheritor : ClassInheritorsSearch.search(psiClass)) {
      if (!inheritor.isInterface() || PsiUtil.isLanguageLevel8OrHigher(inheritor)) {
        final PsiSubstitutor classSubstitutor = TypeConversionUtil.getClassSubstitutor(psiClass, inheritor, PsiSubstitutor.EMPTY);
        PsiMethod method = classSubstitutor != null ? MethodSignatureUtil.findMethodBySignature(inheritor, myMethod.getSignature(classSubstitutor), true)
                                                    : inheritor.findMethodBySignature(myMethod, true);
        if (method == null) continue;
        PsiClass containingClass = method.getContainingClass();
        if (!psiClass.equals(containingClass)) {
          if (containingClass != null) {
            classNamesWithPotentialImplementations.add(PsiFormatUtil.formatClass(containingClass, PsiFormatUtilBase.SHOW_NAME));
          }
          continue;
        }
        list.add(inheritor);
      }
    }

    if (!classNamesWithPotentialImplementations.isEmpty()) {
      @NlsSafe String classNames = StringUtil.join(classNamesWithPotentialImplementations, ", ");
      problemDetected.set(JavaBundle.message("implement.abstract.method.potential.implementations.with.weaker.access", classNames));
    }
    return list.toArray(PsiClass.EMPTY_ARRAY);
  }

  private static class MyPsiElementListCellRenderer extends PsiElementListCellRenderer<PsiElement> {
    private final PsiElementRenderingInfo<PsiClass> myInfo = PsiClassRenderingInfo.INSTANCE;

    void sort(PsiElement[] result) {
      final Comparator<PsiClass> comparator = PsiElementRenderingInfo.getComparator(myInfo);
      Arrays.sort(result, (o1, o2) -> {
        if (o1 instanceof PsiEnumConstant && o2 instanceof PsiEnumConstant) {
          return ((PsiEnumConstant)o1).getName().compareTo(((PsiEnumConstant)o2).getName());
        }
        if (o1 instanceof PsiEnumConstant) return -1;
        if (o2 instanceof PsiEnumConstant) return 1;
        return comparator.compare((PsiClass)o1, (PsiClass)o2);
      });
    }

    @Override
    public String getElementText(PsiElement element) {
      return element instanceof PsiClass ? myInfo.getPresentableText((PsiClass)element)
                                         : ((PsiEnumConstant)element).getName();
    }

    @Override
    protected String getContainerText(PsiElement element, String name) {
      return element instanceof PsiClass ? PsiClassRenderingInfo.getContainerTextStatic(element)
                                         : ((PsiEnumConstant)element).getContainingClass().getQualifiedName();
    }
  }
}
