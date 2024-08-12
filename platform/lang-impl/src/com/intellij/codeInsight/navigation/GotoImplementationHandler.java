// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.NavigateAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.model.Pointer;
import com.intellij.model.psi.impl.UtilKt;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.function.Consumer;

public class GotoImplementationHandler extends GotoTargetHandler {
  @Override
  protected String getFeatureUsedKey() {
    return null;
  }

  @Override
  public @Nullable GotoData getSourceAndTargetElements(@NotNull Editor editor, PsiFile file) {
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
        @Nullable ItemWithPresentation oneElement = getTheOnlyOneElement();
        if (oneElement != null && oneElement.getItem() instanceof SmartPsiElementPointer<?> &&
            navigateToElement(((SmartPsiElementPointer<?>)oneElement.getItem()).getElement())) {
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
      if (renderer instanceof LineMarkerInfo.LineMarkerGutterIconRenderer lineMarkerRenderer) {
        AnAction clickAction = lineMarkerRenderer.getClickAction();
        if (clickAction instanceof NavigateAction && actionId.equals(((NavigateAction<?>)clickAction).getOriginalActionId())) {
          ContainerUtil.addIfNotNull(elementCandidates, lineMarkerRenderer.getLineMarkerInfo().getElement());
        }
      }
    }
    if (elementCandidates.size() == 1) {
      return elementCandidates.get(0).getTextRange().getStartOffset();
    }
    return -1;
  }

  @Override
  protected void chooseFromAmbiguousSources(Editor editor, PsiFile file, Consumer<? super GotoData> successCallback) {
    int offset = editor.getCaretModel().getOffset();
    PsiElementProcessor<PsiElement> navigateProcessor = element -> {
      GotoData data = createDataForSource(editor, offset, element);
      successCallback.accept(data);
      return true;
    };
    GotoDeclarationAction.chooseAmbiguousTarget(editor, offset, navigateProcessor, CodeInsightBundle.message("declaration.navigation.title"), null);
  }

  private static PsiElement getContainer(PsiElement refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensions()) {
      final PsiElement container = provider.getContainer(refElement);
      if (container != null) return container;
    }
    return refElement.getParent();
  }

  @Override
  protected @NotNull String getChooserTitle(@NotNull PsiElement sourceElement, @Nullable String name, int length, boolean finished) {
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

  @Override
  protected @NotNull String getFindUsagesTitle(@NotNull PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.implementation.findUsages.title", StringUtil.escapeXmlEntities(name), length);
  }

  @Override
  protected @NotNull String getNotFoundMessage(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return CodeInsightBundle.message("goto.implementation.notFound");
  }

  public void navigateToImplementations(@NotNull PsiElement baseElement,
                                        @NotNull MouseEvent e,
                                        @NlsContexts.PopupContent String dumbModeMessage) {
    Project project = baseElement.getProject();
    if (DumbService.isDumb(project)) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(dumbModeMessage, DumbModeBlockedFunctionality.GotoImplementations);
      return;
    }
    PsiUtilCore.ensureValid(baseElement);
    PsiFile containingFile = baseElement.getContainingFile();
    //for compiled files a decompiled copy is analysed in the editor (see TextEditorBackgroundHighlighter.getPasses)
    //but it's a non-physical copy with disabled events (see ClsFileImpl.getDecompiledPsiFile)
    //which in turn means that no document can be found for such file - it's required to restore original file
    //other non-physical copies should not be opened in the editor
    PsiFile originalFile = containingFile.getOriginalFile();
    Editor editor = UtilKt.mockEditor(originalFile);
    GotoData source = createDataForSource(Objects.requireNonNull(editor, "No document for " + containingFile + "; original: " + originalFile), baseElement.getTextOffset(), baseElement);

    if (source.isCanceled) return;
    if (source.targets.length == 0) {
      String message = getNotFoundMessage(project, editor, containingFile);
      // Cannot call showEditorHint, as it doesn't work for mockEditor instance
      JComponent label = HintUtil.createErrorLabel(message);
      label.setBorder(HintUtil.createHintBorder());
      int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
      HintManager.getInstance().showHint(label, new RelativePoint(e), flags, 0);
      return;
    }

    showNotEmpty(project, source, popup -> popup.show(new RelativePoint(e)));
  }

  @TestOnly
  public GotoData createDataForSourceForTests(Editor editor, PsiElement element) {
    return createDataForSource(editor, element.getTextOffset(), element);
  }

  private class ImplementationsUpdaterTask extends BackgroundUpdaterTaskBase<ItemWithPresentation> {
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
    public void run(final @NotNull ProgressIndicator indicator) {
      super.run(indicator);
      for (ItemWithPresentation item : myGotoData.getItems()) {
        if (!updateComponent(item)) {
          return;
        }
      }
      new ImplementationSearcher.BackgroundableImplementationSearcher() {
        @Override
        protected void processElement(PsiElement element) {
          indicator.checkCanceled();
          if (!TargetElementUtil.getInstance().acceptImplementationForReference(myReference, element)) return;
          ItemWithPresentation item = myGotoData.addTarget(element);
          if (item != null) {
            if (!updateComponent(item)) {
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

    @Override
    protected @Nullable Usage createUsage(@NotNull ItemWithPresentation element) {
      if (element.getItem() instanceof Pointer<?>) {
        PsiElement psiElement = (PsiElement)((Pointer<?>)element.getItem()).dereference();
        return psiElement == null ? null : new UsageInfo2UsageAdapter(new UsageInfo(psiElement));
      }
      return null;
    }
  }

  private static @Nullable Comparator<ItemWithPresentation> createImplementationComparator(@NotNull GotoData gotoData) {
    Comparator<ItemWithPresentation> projectContentComparator = wrapPsiComparator(projectElementsFirst(gotoData.source.getProject()));
    Comparator<ItemWithPresentation> presentationComparator = Comparator.comparing(element -> gotoData.getComparingObject(element));
    Comparator<ItemWithPresentation> positionComparator = wrapPsiComparator(PsiUtilCore::compareElementsByPosition);
    return projectContentComparator.thenComparing(presentationComparator).thenComparing(positionComparator);
  }

  private static @NotNull Comparator<ItemWithPresentation> wrapPsiComparator(Comparator<PsiElement> result) {
    Comparator<ItemWithPresentation> comparator = (o1, o2) -> {
      if (o1.getItem() instanceof SmartPsiElementPointer<?> && o2.getItem() instanceof SmartPsiElementPointer<?>) {
        return ReadAction.compute(() -> result.compare(((SmartPsiElementPointer<?>)o1.getItem()).getElement(), ((SmartPsiElementPointer<?>)o2.getItem()).getElement()));
      }
      return 0;
    };
    return comparator;
  }

  public static @NotNull Comparator<PsiElement> projectElementsFirst(@NotNull Project project) {
    FileIndexFacade index = FileIndexFacade.getInstance(project);
    return Comparator.comparing((PsiElement element) -> {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile != null) {
        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile != null && index.isInContent(virtualFile)) return true;
      }
      return false;
    }).reversed();
  }

  public static <T> @NotNull Comparator<T> wrapIntoReadAction(@NotNull Comparator<? super T> base) {
    return (e1, e2) -> ReadAction.compute(() -> base.compare(e1, e2));
  }
}
