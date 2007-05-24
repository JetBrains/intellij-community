
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import java.util.*;

public class OverriddenMarkersPass extends TextEditorHighlightingPass {
  private static final Icon OVERRIDEN_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/overridenMethod.png");
  private static final Icon IMPLEMENTED_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/implementedMethod.png");
  private static final Icon IMPLEMENTED_INTERFACE_MARKER_RENDERER = IconLoader.getIcon("/gutter/implementedMethod.png");
  private static final Icon SUBCLASSED_CLASS_MARKER_RENDERER = IconLoader.getIcon("/gutter/overridenMethod.png");

  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;

  private Collection<LineMarkerInfo> myMarkers;

  public OverriddenMarkersPass(Project project, PsiFile file, Document document, int startOffset, int endOffset) {
    super(project, document);
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getPrimaryLanguages();
    myMarkers = new SmartList<LineMarkerInfo>();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if (!HighlightUtil.shouldHighlight(psiRoot)) continue;
      List<PsiElement> elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      collectLineMarkers(elements, myMarkers);
    }
  }

  public void doApplyInformationToEditor() {
    UpdateHighlightersUtil.setLineMarkersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myMarkers, Pass.UPDATE_OVERRIDEN_MARKERS);

    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, getId());
  }

  private static void collectLineMarkers(List<PsiElement> elements, final Collection<LineMarkerInfo> result) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();
      if (element instanceof PsiIdentifier) {
        if (element.getParent() instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)element.getParent();
          if (element.equals(method.getNameIdentifier()) && PsiUtil.canBeOverriden(method)) {
            methods.add(method);
          }
        }
        else if (element.getParent() instanceof PsiClass && !(element.getParent() instanceof PsiTypeParameter)) {
          collectInheritingClasses(element, result);
        }
      }
    }
    if (!methods.isEmpty()) {
      collectOverridingMethods(methods, result);
    }
  }

  private static void collectInheritingClasses(PsiElement element, Collection<LineMarkerInfo> result) {
    PsiClass aClass = (PsiClass) element.getParent();
    if (element.equals(aClass.getNameIdentifier())) {
      if (!aClass.hasModifierProperty(PsiModifier.FINAL)) {
        if ("java.lang.Object".equals(aClass.getQualifiedName())) return; // It's useless to have overriden markers for object.

        final PsiClass inheritor = ClassInheritorsSearch.search(aClass, false).findFirst();
        if (inheritor != null) {
          int offset = element.getTextRange().getStartOffset();
          LineMarkerInfo info = new LineMarkerInfo(LineMarkerInfo.MarkerType.SUBCLASSED_CLASS, aClass, offset, aClass.isInterface() ? IMPLEMENTED_INTERFACE_MARKER_RENDERER : SUBCLASSED_CLASS_MARKER_RENDERER);

          result.add(info);
        }
      }
    }
  }

  private static void collectOverridingMethods(final Set<PsiMethod> methods, Collection<LineMarkerInfo> result) {
    final Set<PsiMethod> overridden = new HashSet<PsiMethod>();
    Set<PsiClass> classes = new HashSet<PsiClass>();
    for (PsiMethod method : methods) {
      final PsiClass parentClass = method.getContainingClass();
      if (!"java.lang.Object".equals(parentClass.getQualifiedName())) {
        classes.add(parentClass);
      }
    }

    for (final PsiClass aClass : classes) {
      AllOverridingMethodsSearch.search(aClass).forEach(new Processor<Pair<PsiMethod, PsiMethod>>() {
        public boolean process(final Pair<PsiMethod, PsiMethod> pair) {
          final PsiMethod superMethod = pair.getFirst();
          overridden.add(superMethod);
          methods.remove(superMethod);
          return !methods.isEmpty();
        }
      });
    }

    for (PsiMethod method : overridden) {
      boolean overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);

      final PsiIdentifier ident = method.getNameIdentifier();
      assert ident != null; // Can only be null for JspHolderMethod, which cannot be overriden.

      int offset = ident.getTextRange().getStartOffset();
      LineMarkerInfo info = new LineMarkerInfo(LineMarkerInfo.MarkerType.OVERRIDEN_METHOD, method, offset,
                                               overrides ? OVERRIDEN_METHOD_MARKER_RENDERER : IMPLEMENTED_METHOD_MARKER_RENDERER);

      result.add(info);
    }
  }
}