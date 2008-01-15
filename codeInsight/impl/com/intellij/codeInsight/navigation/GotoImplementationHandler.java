package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DefinitionsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;

public class GotoImplementationHandler implements CodeInsightActionHandler {
  public static final int FLAGS = TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
                                  | TargetElementUtil.ELEMENT_NAME_ACCEPTED
                                  | TargetElementUtil.LOOKUP_ITEM_ACCEPTED
                                  | TargetElementUtil.THIS_ACCEPTED
                                  | TargetElementUtil.SUPER_ACCEPTED;

  public void invoke(Project project, Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = TargetElementUtil.findTargetElement(editor, FLAGS, offset);

    PsiElement[] result = searchImplementations(editor, element, offset);
    if (result.length > 0) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.implementation");
      show(editor, element, result);
    }
  }

  public PsiElement[] searchImplementations(final Editor editor, final PsiElement element, final int offset) {
    boolean onRef = TargetElementUtil.findTargetElement(editor, FLAGS & ~TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED, offset) == null;
    final boolean isAbstract =
      element instanceof PsiModifierListOwner && ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.ABSTRACT);
    return searchImplementations(editor, element, offset, onRef && !isAbstract, onRef);
  }

  @NotNull
  public PsiElement[] searchImplementations(@Nullable Editor editor,
                                            final PsiElement element,
                                            int offset,
                                            final boolean includeSelfAlways,
                                            final boolean includeSelfIfNoOthers) {
    if (element == null) return PsiElement.EMPTY_ARRAY;
    final PsiElement[] elements = searchDefinitions(element);
    if (elements == null) return PsiElement.EMPTY_ARRAY; //the search has been cancelled
    if (elements.length > 0) {
      if (!includeSelfAlways) return filterElements(editor, element, elements, offset);
      PsiElement[] all = new PsiElement[elements.length + 1];
      all[0] = element;
      System.arraycopy(elements, 0, all, 1, elements.length);
      return filterElements(editor, element, all, offset);
    }
    return includeSelfAlways || includeSelfIfNoOthers ?
           new PsiElement[] {element} :
           PsiElement.EMPTY_ARRAY;
  }

  @Nullable("For the case the search has been cancelled")
  protected PsiElement[] searchDefinitions(final PsiElement element) {
    final PsiElement[][] result = new PsiElement[1][];
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        result[0] = DefinitionsSearch.search(element).toArray(PsiElement.EMPTY_ARRAY);
      }
    }, CodeInsightBundle.message("searching.for.implementations"), true, element.getProject())) {
      return null;
    }
    return result[0];
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static void getOverridingMethods(PsiMethod method, ArrayList<PsiMethod> list) {
    for (PsiMethod psiMethod : OverridingMethodsSearch.search(method)) {
      list.add(psiMethod);
    }
  }

  protected PsiElement[] filterElements(@Nullable Editor editor, PsiElement element, PsiElement[] targetElements, final int offset) {
    return targetElements;
  }

  static {
    DefinitionsSearch.INSTANCE.registerExecutor(new MethodImplementationsSearch());
    DefinitionsSearch.INSTANCE.registerExecutor(new ClassImplementationsSearch());
  }


  public static PsiClass[] getClassImplementations(final PsiClass psiClass) {
    final ArrayList<PsiClass> list = new ArrayList<PsiClass>();

    ClassInheritorsSearch.search(psiClass, psiClass.getUseScope(), true).forEach(new PsiElementProcessorAdapter<PsiClass>(new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        if (!element.isInterface()) {
          list.add(element);
        }
        return true;
      }
    }));

    return list.toArray(new PsiClass[list.size()]);
  }

  private static class MethodImplementationsSearch implements QueryExecutor<PsiElement, PsiElement> {
    public boolean execute(final PsiElement sourceElement, final Processor<PsiElement> consumer) {
      if (sourceElement instanceof PsiMethod) {
        for (PsiElement implementation : getMethodImplementations((PsiMethod)sourceElement)) {
          if ( ! consumer.process(implementation) ) {
            return false;
          }
        }
      }
      return true;
    }
  }
  private static class ClassImplementationsSearch implements QueryExecutor<PsiElement, PsiElement> {
    public boolean execute(final PsiElement sourceElement, final Processor<PsiElement> consumer) {
      if (sourceElement instanceof PsiClass) {
        for (PsiElement implementation : getClassImplementations((PsiClass)sourceElement)) {
          if ( ! consumer.process(implementation) ) {
            return false;
          }
        }
      }
      return true;
    }
  }

  public static PsiMethod[] getMethodImplementations(final PsiMethod method) {
    ArrayList<PsiMethod> result = new ArrayList<PsiMethod>();

    getOverridingMethods(method, result);
    return result.toArray(new PsiMethod[result.size()]);
  }

  private static void show(Editor editor, final PsiElement sourceElement, final PsiElement[] elements) {
    if (elements == null || elements.length == 0) {
      return;
    }

    if (elements.length == 1) {
      Navigatable descriptor = EditSourceUtil.getDescriptor(elements[0]);
      if (descriptor != null && descriptor.canNavigate()) {
        descriptor.navigate(true);
      }
    }
    else {
      boolean onlyMethods = true;
      boolean onlyClasses = true;
      for (PsiElement element : elements) {
        if (!(element instanceof PsiMethod)) onlyMethods = false;
        if (!(element instanceof PsiClass)) onlyClasses = false;
      }
      PsiElementListCellRenderer renderer;
      if (onlyMethods) {
        renderer = new MethodCellRenderer(!PsiUtil.allMethodsHaveSameSignature(Arrays.asList(elements).toArray(PsiMethod.EMPTY_ARRAY)));
      }
      else if (onlyClasses) {
        renderer = new PsiClassListCellRenderer();
      }
      else {
        renderer = new DefaultPsiElementListCellRenderer();
      }

      Arrays.sort(elements, renderer.getComparator());

      final JList list = new JList(elements);
      list.setCellRenderer(renderer);

      renderer.installSpeedSearch(list);

      final Runnable runnable = new Runnable() {
        public void run() {
          int[] ids = list.getSelectedIndices();
          if (ids == null || ids.length == 0) return;
          Object[] selectedElements = list.getSelectedValues();
          for (Object element : selectedElements) {
            Navigatable descriptor = EditSourceUtil.getDescriptor((PsiElement)element);
            if (descriptor != null && descriptor.canNavigate()) {
              descriptor.navigate(true);
            }
          }
        }
      };

      final String name = ((PsiNamedElement)sourceElement).getName();
      final String title;
      if (onlyMethods || onlyClasses) {
        title = CodeInsightBundle.message("goto.implementation.chooser.title", name, elements.length);
      }
      else {
        title = CodeInsightBundle.message("goto.implementation.in.file.chooser.title", name, elements.length);
      }
      new PopupChooserBuilder(list).
        setTitle(title).
        setItemChoosenCallback(runnable).
        createPopup().showInBestPositionFor(editor);
    }
  }

  private static class DefaultPsiElementListCellRenderer extends PsiElementListCellRenderer {
    public String getElementText(final PsiElement element) {
      return element.getContainingFile().getName();
    }

    protected String getContainerText(final PsiElement element, final String name) {
      return null;
    }

    protected int getIconFlags() {
      return 0;
    }
  }
}
