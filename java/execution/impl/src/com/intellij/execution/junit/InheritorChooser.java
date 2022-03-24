// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.util.PopupUtilsKt.getBestPopupPosition;

public class InheritorChooser {

  protected void runForClasses(final List<PsiClass> classes, final PsiMethod method, final ConfigurationContext context, final Runnable performRunnable) {
    performRunnable.run();
  }

  protected void runForClass(final PsiClass aClass, final PsiMethod psiMethod, final ConfigurationContext context, final Runnable performRunnable) {
    performRunnable.run();
  }

  public boolean runMethodInAbstractClass(final ConfigurationContext context,
                                          final Runnable performRunnable,
                                          final PsiMethod psiMethod,
                                          final PsiClass containingClass) {
    return runMethodInAbstractClass(context, performRunnable, psiMethod, containingClass,
                                    psiClass -> psiClass.hasModifierProperty(PsiModifier.ABSTRACT));
  }

  public boolean runMethodInAbstractClass(final ConfigurationContext context,
                                          final Runnable performRunnable,
                                          final PsiMethod psiMethod,
                                          final PsiClass containingClass,
                                          final Condition<? super PsiClass> acceptAbstractCondition) {
    if (containingClass != null && acceptAbstractCondition.value(containingClass)) {
      final Location location = context.getLocation();
      if (location instanceof MethodLocation) {
        final PsiClass aClass = ((MethodLocation)location).getContainingClass();
        if (aClass != null && !aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          return false;
        }
      } else if (location instanceof PsiMemberParameterizedLocation) {
        return false;
      }

      final List<PsiClass> classes = new ArrayList<>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        collectClasses(containingClass, classes, new HashSet<>());
      }, ExecutionBundle.message("search.for.0.inheritors", containingClass.getQualifiedName()), true, containingClass.getProject())) {
        return true;
      }

      if (classes.size() == 1) {
        runForClass(classes.get(0), psiMethod, context, performRunnable);
        return true;
      }
      if (classes.isEmpty()) return false;
      final FileEditor fileEditor = PlatformCoreDataKeys.FILE_EDITOR.getData(context.getDataContext());
      if (fileEditor instanceof TextEditor) {
        final Document document = ((TextEditor)fileEditor).getEditor().getDocument();
        final PsiFile containingFile = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(document);
        if (containingFile instanceof PsiClassOwner) {
          final List<PsiClass> psiClasses = new ArrayList<>(Arrays.asList(((PsiClassOwner)containingFile).getClasses()));
          psiClasses.retainAll(classes);
          if (psiClasses.size() == 1) {
            runForClass(psiClasses.get(0), psiMethod, context, performRunnable);
            return true;
          }
        }
      }
      final int numberOfInheritors = classes.size();
      final PsiClassListCellRenderer renderer = new PsiClassListCellRenderer() {
        @Override
        protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer,
                                                             JList list,
                                                             Object value,
                                                             int index,
                                                             boolean selected,
                                                             boolean hasFocus) {
          if (value == null) {
            renderer.append(JavaCompilerBundle.message("all.inheritors", numberOfInheritors));
            return true;
          }
          return super.customizeNonPsiElementLeftRenderer(renderer, list, value, index, selected, hasFocus);
        }
      };
      classes.sort(renderer.getComparator());

      //suggest to run all inherited tests
      classes.add(0, null);
      String locationName = psiMethod != null ? psiMethod.getName() : containingClass.getName();
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(classes)
        .setRenderer(renderer)
        .setTitle(ExecutionBundle.message("test.cases.choosing.popup.title", locationName))
        .setAutoselectOnMouseMove(false)
        .setNamerForFiltering(it -> it == null ? "" : it.getName())
        .setMovable(true)
        .setResizable(false)
        .setRequestFocus(true)
        .setMinSize(JBUI.size(270, 55))
        .setItemsChosenCallback((values) -> {
          if (values.isEmpty()) return;
          chooseAndPerform(values.toArray(), psiMethod, context, performRunnable, classes);
        })
        .createPopup()
        .show(getBestPopupPosition(context.getDataContext()));
      return true;
    }
    
    if (containingClass != null) {
      return runMethodInAbstractClass(context, performRunnable, psiMethod, containingClass.getContainingClass(), acceptAbstractCondition);
    }
    return false;
  }

  private static void collectClasses(PsiClass containingClass, List<PsiClass> classes, HashSet<PsiClass> visited) {
    if (!visited.add(containingClass)) return;
    Set<PsiClass> containers = new HashSet<>();
    final boolean isJUnit5 = ReadAction.compute(() -> JUnitUtil.isJUnit5(containingClass));
    ClassInheritorsSearch.search(containingClass).forEach(aClass -> {
      PsiClass container = aClass.getContainingClass();
      if (container != null && container.hasModifierProperty(PsiModifier.ABSTRACT)) {
        containers.add(container);
      }
      else if (isJUnit5 && JUnitUtil.isJUnit5TestClass(aClass, true) || PsiClassUtil.isRunnableClass(aClass, true, true)) {
        classes.add(aClass);
      }
      return true;
    });

    for (PsiClass container : containers) {
      collectClasses(container, classes, visited);
    }
  }

  private void chooseAndPerform(Object[] values,
                                PsiMethod psiMethod,
                                ConfigurationContext context,
                                Runnable performRunnable,
                                List<PsiClass> classes) {
    classes.remove(null);
    if (values.length == 1) {
      final Object value = values[0];
      if (value instanceof PsiClass) {
        runForClass((PsiClass)value, psiMethod, context, performRunnable);
      }
      else {
        runForClasses(classes, psiMethod, context, performRunnable);
      }
      return;
    }
    if (ArrayUtil.contains(null, values)) {
      runForClasses(classes, psiMethod, context, performRunnable);
    }
    else {
      final List<PsiClass> selectedClasses = new ArrayList<>();
      for (Object value : values) {
        if (value instanceof PsiClass) {
          selectedClasses.add((PsiClass)value);
        }
      }
      runForClasses(selectedClasses, psiMethod, context, performRunnable);
    }
  }

  public static void chooseAbstractClassInheritors(final ConfigurationContext context,
                                                   final PsiClass psiClass,
                                                   final Consumer<List<PsiClass>> onClassesChosen) {
    InheritorChooser inheritorChooser = new InheritorChooser() {
      @Override
      protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
        onClassesChosen.accept(classes);
      }

      @Override
      protected void runForClass(PsiClass aClass, PsiMethod psiMethod, ConfigurationContext context, Runnable performRunnable) {
        onClassesChosen.accept(List.of(aClass));
      }
    };
    if (!inheritorChooser.runMethodInAbstractClass(context, () -> {}, null, psiClass)) {
      onClassesChosen.accept(List.of(psiClass));
    }
  }
}