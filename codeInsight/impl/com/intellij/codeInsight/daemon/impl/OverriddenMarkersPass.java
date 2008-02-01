
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
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
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class OverriddenMarkersPass extends TextEditorHighlightingPass {
  private static final Icon OVERRIDEN_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/overridenMethod.png");
  private static final Icon IMPLEMENTED_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/implementedMethod.png");
  private static final Icon IMPLEMENTED_INTERFACE_MARKER_RENDERER = IMPLEMENTED_METHOD_MARKER_RENDERER;
  private static final Icon SUBCLASSED_CLASS_MARKER_RENDERER = OVERRIDEN_METHOD_MARKER_RENDERER;

  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;

  private Collection<LineMarkerInfo> myMarkers;

  public OverriddenMarkersPass(@NotNull Project project, @NotNull PsiFile file, @NotNull Document document, int startOffset, int
endOffset) {
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
      if (!HighlightLevelUtil.shouldHighlight(psiRoot)) continue;
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      collectLineMarkers(elements, myMarkers);
    }
  }

  public void doApplyInformationToEditor() {
    UpdateHighlightersUtil.setLineMarkersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myMarkers, getId());

    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, getId());
  }

  private static void collectLineMarkers(List<PsiElement> elements, final Collection<LineMarkerInfo> result) throws
ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();
      if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)element;
        if (PsiUtil.canBeOverriden(method)) {
          methods.add(method);
        }
      }
      else if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
        collectInheritingClasses((PsiClass)element, result);
      }
    }
    if (!methods.isEmpty()) {
      collectOverridingMethods(methods, result);
    }
  }

  private static void collectInheritingClasses(PsiClass aClass, Collection<LineMarkerInfo> result) {
    if (!aClass.hasModifierProperty(PsiModifier.FINAL)) {
      if ("java.lang.Object".equals(aClass.getQualifiedName())) return; // It's useless to have overriden markers for object.

      final PsiClass inheritor = ClassInheritorsSearch.search(aClass, false).findFirst();
      if (inheritor != null) {
        int offset = aClass.getTextOffset();
        final Icon icon = aClass.isInterface() ? IMPLEMENTED_INTERFACE_MARKER_RENDERER : SUBCLASSED_CLASS_MARKER_RENDERER;
        final MarkerType type = MarkerType.SUBCLASSED_CLASS;
        Function<PsiElement, String> tooltip = new ClassGutterIconTooltipProvider(type);
        LineMarkerInfo info = new LineMarkerInfo(aClass, offset, icon, Pass.UPDATE_OVERRIDEN_MARKERS, tooltip, new GutterNavigationHandlerImpl(type));
        result.add(info);
      }
    }
  }

  private static void collectOverridingMethods(final Set<PsiMethod> methods, Collection<LineMarkerInfo> result) {
    final Set<PsiMethod> overridden = new HashSet<PsiMethod>();
    Set<PsiClass> classes = new THashSet<PsiClass>();
    for (PsiMethod method : methods) {
      ProgressManager.getInstance().checkCanceled();
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

      int offset = method.getTextOffset();
      final Icon icon = overrides ? OVERRIDEN_METHOD_MARKER_RENDERER : IMPLEMENTED_METHOD_MARKER_RENDERER;
      final MarkerType type = MarkerType.OVERRIDEN_METHOD;
      Function<PsiElement, String> tooltip = new MethodGutterIconTooltipProvider(type);
      LineMarkerInfo info = new LineMarkerInfo(method, offset, icon, Pass.UPDATE_OVERRIDEN_MARKERS, tooltip, new GutterNavigationHandlerImpl(type));
      result.add(info);
    }
  }
}

