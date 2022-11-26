// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.NavigateAction;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GotoImplementationHandler extends GotoTargetHandler {
  @Override
  protected String getFeatureUsedKey() {
    return null;
  }

  @Override
  @Nullable
  public GotoData getSourceAndTargetElements(@NotNull Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement source = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
    if (source == null) {
      offset = tryGetNavigationSourceOffsetFromGutterIcon(editor, IdeActions.ACTION_GOTO_IMPLEMENTATION);
      if (offset >= 0) {
        source = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
      }
    }
    if (source == null) return null;
    return createDataForSource(editor, offset, source);
  }

  protected @NotNull GotoData createDataForSource(@NotNull Editor editor, int offset, PsiElement source) {
    final PsiElement[] targets = findTargets(editor, offset, source);
    if (targets == null) {
      //canceled search
      GotoData data = new GotoData(source, PsiElement.EMPTY_ARRAY, Collections.emptyList());
      data.isCanceled = true;
      return data;
    }
    final PsiReference reference = TargetElementUtil.findReference(editor, offset);
    GotoData gotoData = new GotoData(source, targets, Collections.emptyList());
    gotoData.listUpdaterTask = new ImplementationsUpdaterTask(gotoData, editor, offset, reference) {
      @Override
      public void onSuccess() {
        super.onSuccess();
        PsiElement oneElement = getTheOnlyOneElement();
        if (oneElement != null && navigateToElement(oneElement)) {
          myPopup.cancel();
        }
      }
    };
    return gotoData;
  }

  protected PsiElement @Nullable [] findTargets(@NotNull Editor editor, int offset, @NotNull PsiElement source) {
    final PsiReference reference = TargetElementUtil.findReference(editor, offset);
    final TargetElementUtil instance = TargetElementUtil.getInstance();
    return new ImplementationSearcher.FirstImplementationsSearcher() {
      @Override
      protected boolean accept(PsiElement element) {
        if (reference != null && !reference.getElement().isValid()) return false;
        return instance.acceptImplementationForReference(reference, element);
      }

      @Override
      protected boolean canShowPopupWithOneItem(PsiElement element) {
        return false;
      }
    }.searchImplementations(editor, source, offset);
  }

  public static int tryGetNavigationSourceOffsetFromGutterIcon(@NotNull Editor editor, String actionId) {
    int line = editor.getCaretModel().getVisualPosition().line;
    List<GutterMark> renderers = ((EditorGutterComponentEx)editor.getGutter()).getGutterRenderers(line);
    List<PsiElement> elementCandidates = new ArrayList<>();
    for (GutterMark renderer : renderers) {
      if (renderer instanceof LineMarkerInfo.LineMarkerGutterIconRenderer) {
        LineMarkerInfo.LineMarkerGutterIconRenderer lineMarkerRenderer = (LineMarkerInfo.LineMarkerGutterIconRenderer)renderer;
        AnAction clickAction = ((LineMarkerInfo.LineMarkerGutterIconRenderer<?>)renderer).getClickAction();
        if (clickAction instanceof NavigateAction && actionId.equals(((NavigateAction<?>)clickAction).getOriginalActionId())) {
          elementCandidates.add(lineMarkerRenderer.getLineMarkerInfo().getElement());
        }
      }
    }
    if (elementCandidates.size() == 1) {
      return elementCandidates.iterator().next().getTextRange().getStartOffset();
    }
    return -1;
  }

  @Override
  protected void chooseFromAmbiguousSources(Editor editor, PsiFile file, Consumer<? super GotoData> successCallback) {
    int offset = editor.getCaretModel().getOffset();
    PsiElementProcessor<PsiElement> navigateProcessor = element -> {
      GotoData data = createDataForSource(editor, offset, element);
      successCallback.consume(data);
      return true;
    };
    Project project = editor.getProject();
    if (project == null) return;

    GotoDeclarationAction
      .chooseAmbiguousTarget(project, editor, offset, navigateProcessor, CodeInsightBundle.message("declaration.navigation.title"), null);
  }

  private static PsiElement getContainer(PsiElement refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensions()) {
      final PsiElement container = provider.getContainer(refElement);
      if (container != null) return container;
    }
    return refElement.getParent();
  }

  @Override
  @NotNull
  protected String getChooserTitle(@NotNull PsiElement sourceElement, @Nullable String name, int length, boolean finished) {
    ItemPresentation presentation = ((NavigationItem)sourceElement).getPresentation();
    String fullName;
    if (presentation == null) {
      fullName = name;
    }
    else {
      PsiElement container = getContainer(sourceElement);
      ItemPresentation containerPresentation = container == null || container instanceof PsiFile ? null : ((NavigationItem)container).getPresentation();
      String containerText = containerPresentation == null ? null : containerPresentation.getPresentableText();
      fullName = (containerText == null ? "" : containerText+".") + presentation.getPresentableText();
    }
    return CodeInsightBundle.message("goto.implementation.chooserTitle",
                                     fullName == null ? "unnamed element" : StringUtil.escapeXmlEntities(fullName), length, finished ? "" : " so far");
  }

  @NotNull
  @Override
  protected String getFindUsagesTitle(@NotNull PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.implementation.findUsages.title", StringUtil.escapeXmlEntities(name), length);
  }

  @NotNull
  @Override
  protected String getNotFoundMessage(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return CodeInsightBundle.message("goto.implementation.notFound");
  }

  private class ImplementationsUpdaterTask extends BackgroundUpdaterTask {
    private final Editor myEditor;
    private final int myOffset;
    private final GotoData myGotoData;
    private final PsiReference myReference;

    ImplementationsUpdaterTask(@NotNull GotoData gotoData, @NotNull Editor editor, int offset, final PsiReference reference) {
      super(
        gotoData.source.getProject(),
        ImplementationSearcher.getSearchingForImplementations(),
        createImplementationComparator(gotoData)
      );
      myEditor = editor;
      myOffset = offset;
      myGotoData = gotoData;
      myReference = reference;
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      for (PsiElement element : myGotoData.targets) {
        if (!updateComponent(element)) {
          return;
        }
      }
      new ImplementationSearcher.BackgroundableImplementationSearcher() {
        @Override
        protected void processElement(PsiElement element) {
          indicator.checkCanceled();
          if (!TargetElementUtil.getInstance().acceptImplementationForReference(myReference, element)) return;
          if (myGotoData.addTarget(element)) {
            if (!updateComponent(element)) {
              indicator.cancel();
            }
          }
        }
      }.searchImplementations(myEditor, myGotoData.source, myOffset);
    }

    @Override
    public String getCaption(int size) {
      String name = ElementDescriptionUtil.getElementDescription(myGotoData.source, UsageViewShortNameLocation.INSTANCE);
      return getChooserTitle(myGotoData.source, name, size, isFinished());
    }
  }

  private static @NotNull Comparator<PsiElement> createImplementationComparator(@NotNull GotoData gotoData) {
    Comparator<PsiElement> projectContentComparator = projectElementsFirst(gotoData.source.getProject());
    Comparator<PsiElement> presentationComparator = Comparator.comparing(element -> gotoData.getComparingObject(element));
    Comparator<PsiElement> positionComparator = PsiUtilCore::compareElementsByPosition;
    Comparator<PsiElement> result = projectContentComparator.thenComparing(presentationComparator).thenComparing(positionComparator);
    return wrapIntoReadAction(result);
  }

  public static @NotNull Comparator<PsiElement> projectElementsFirst(@NotNull Project project) {
    FileIndexFacade index = FileIndexFacade.getInstance(project);
    return Comparator.comparing((PsiElement element) -> index.isInContent(element.getContainingFile().getVirtualFile())).reversed();
  }

  private static <T> @NotNull Comparator<T> wrapIntoReadAction(@NotNull Comparator<? super T> base) {
    return (e1, e2) -> ReadAction.compute(() -> base.compare(e1, e2));
  }
}
