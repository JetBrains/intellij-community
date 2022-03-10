// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindUtil;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.TargetPresentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usages.UsageView;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class GotoTargetHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance(GotoTargetHandler.class);
  private static final PsiElementListCellRenderer<?> ourDefaultTargetElementRenderer = new DefaultPsiElementListCellRenderer();

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(getFeatureUsedKey());

    try {
      GotoData gotoData = getSourceAndTargetElements(editor, file);
      if (gotoData != null) {
        show(project, editor, file, gotoData);
      }
      else {
        chooseFromAmbiguousSources(editor, file, data -> show(project, editor, file, data));
      }
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotification(
        CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"));
    }
  }

  protected void chooseFromAmbiguousSources(Editor editor, PsiFile file, Consumer<? super GotoData> successCallback) { }

  @NonNls
  protected abstract String getFeatureUsedKey();

  protected boolean useEditorFont() {
    return true;
  }

  @Nullable
  protected abstract GotoData getSourceAndTargetElements(Editor editor, PsiFile file);

  private void show(@NotNull Project project,
                    @NotNull Editor editor,
                    @NotNull PsiFile file,
                    @NotNull GotoData gotoData) {
    if (gotoData.isCanceled) return;

    PsiElement[] targets = gotoData.targets;
    List<AdditionalAction> additionalActions = gotoData.additionalActions;

    if (targets.length == 0 && additionalActions.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, getNotFoundMessage(project, editor, file));
      return;
    }

    boolean finished = gotoData.listUpdaterTask == null || gotoData.listUpdaterTask.isFinished();
    if (targets.length == 1 && additionalActions.isEmpty() && finished) {
      navigateToElement(targets[0]);
      return;
    }

    gotoData.initPresentations();

    final String name = ((NavigationItem)gotoData.source).getName();
    final String title = getChooserTitle(gotoData.source, name, targets.length, finished);

    if (shouldSortTargets()) {
      Arrays.sort(targets, createComparator(gotoData));
    }

    List<Object> allElements = new ArrayList<>(targets.length + additionalActions.size());
    Collections.addAll(allElements, targets);
    allElements.addAll(additionalActions);

    final IPopupChooserBuilder<Object> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(allElements);
    final Ref<UsageView> usageView = new Ref<>();
    builder.setNamerForFiltering(o -> {
      if (o instanceof AdditionalAction) {
        return ((AdditionalAction)o).getText();
      }
      return gotoData.getPresentation(o).getPresentableText();
    }).setTitle(title);
    if (useEditorFont()) {
      builder.setFont(EditorUtil.getEditorFont());
    }
    builder.setRenderer(new GotoTargetRenderer(gotoData::getPresentation)).
      setItemsChosenCallback(selectedElements -> {
        for (Object element : selectedElements) {
          if (element instanceof AdditionalAction) {
            ((AdditionalAction)element).execute();
          }
          else {
            Navigatable nav = element instanceof Navigatable ? (Navigatable)element : EditSourceUtil.getDescriptor((PsiElement)element);
            try {
              if (nav != null && nav.canNavigate()) {
                navigateToElement(nav);
              }
            }
            catch (IndexNotReadyException e) {
              DumbService.getInstance(project).showDumbModeNotification(
                CodeInsightBundle.message("notification.navigation.is.not.available.while.indexing"));
            }
          }
        }
      }).
      withHintUpdateSupply().
      setMovable(true).
      setCancelCallback(() -> {
        final BackgroundUpdaterTask task = gotoData.listUpdaterTask;
        if (task != null) {
          task.cancelTask();
        }
        return true;
      }).
      setCouldPin(popup1 -> {
        usageView.set(FindUtil.showInUsageView(gotoData.source, gotoData.targets,
                                               getFindUsagesTitle(gotoData.source, name, gotoData.targets.length),
                                               gotoData.source.getProject()));
        popup1.cancel();
        return false;
      }).
      setAdText(getAdText(gotoData.source, targets.length));
    final JBPopup popup = builder.createPopup();

    JScrollPane pane = builder instanceof PopupChooserBuilder ? ((PopupChooserBuilder<?>)builder).getScrollPane() : null;
    if (pane != null) {
      pane.setBorder(null);
      pane.setViewportBorder(null);
    }

    if (gotoData.listUpdaterTask != null) {
      Alarm alarm = new Alarm(popup);
      alarm.addRequest(() -> {
        if (!editor.isDisposed()) {
          popup.showInBestPositionFor(editor);
        }
      }, 300);
      gotoData.listUpdaterTask.init(popup, builder.getBackgroundUpdater(), usageView);
      ProgressManager.getInstance().run(gotoData.listUpdaterTask);
    }
    else {
      popup.showInBestPositionFor(editor);
    }
  }

  @NotNull
  protected Comparator<PsiElement> createComparator(@NotNull GotoData gotoData) {
    return Comparator.comparing(gotoData::getComparingObject);
  }

  private static Map<PsiElement, TargetPresentation> computePresentationInBackground(
    @NotNull Project project,
    PsiElement @NotNull [] targets,
    boolean hasDifferentNames
  ) {
    return ActionUtil.underModalProgress(project, CodeInsightBundle.message("progress.title.preparing.result"), () -> {
      Map<PsiElement, TargetPresentation> presentations = new HashMap<>();
      for (PsiElement eachTarget : targets) {
        presentations.put(eachTarget, computePresentation(eachTarget, hasDifferentNames));
      }
      return presentations;
    });
  }

  public static @NotNull TargetPresentation computePresentation(@NotNull PsiElement element, boolean hasDifferentNames) {
    TargetPresentation presentation = GotoTargetPresentationProvider.getTargetPresentationFromProviders(element, hasDifferentNames);
    if (presentation != null) return presentation;
    TargetPresentation renderer = getTargetPresentationFromRenderers(element, hasDifferentNames);
    if (renderer != null) return renderer;
    return ourDefaultTargetElementRenderer.computePresentation(element);
  }

  @Nullable
  private static TargetPresentation getTargetPresentationFromRenderers(@NotNull PsiElement element, boolean hasDifferentNames) {
    GotoData dummyData = new GotoData(element, PsiElement.EMPTY_ARRAY, Collections.emptyList());
    dummyData.hasDifferentNames = hasDifferentNames;
    PsiElementListCellRenderer<?> renderer = createRenderer(dummyData, element);
    return renderer == null ? null : renderer.computePresentation(element);
  }

  /**
   * @deprecated use {@link #computePresentation}
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("rawtypes")
  public static PsiElementListCellRenderer createRenderer(@NotNull GotoData gotoData, @NotNull PsiElement eachTarget) {
    for (GotoTargetRendererProvider eachProvider : GotoTargetRendererProvider.EP_NAME.getExtensionList()) {
      PsiElementListCellRenderer renderer = eachProvider.getRenderer(eachTarget, gotoData);
      if (renderer != null) return renderer;
    }
    return null;
  }

  protected boolean navigateToElement(PsiElement target) {
    Navigatable descriptor = target instanceof Navigatable ? (Navigatable)target : EditSourceUtil.getDescriptor(target);
    if (descriptor != null && descriptor.canNavigate()) {
      navigateToElement(descriptor);
      return true;
    }
    return false;
  }

  protected void navigateToElement(@NotNull Navigatable descriptor) {
    descriptor.navigate(true);
  }

  protected boolean shouldSortTargets() {
    return true;
  }


  /**
   * @deprecated, use getChooserTitle(PsiElement, String, int, boolean) instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  protected @NlsContexts.PopupTitle String getChooserTitle(PsiElement sourceElement, String name, int length) {
    LOG.warn("Please override getChooserTitle(PsiElement, String, int, boolean) instead");
    return "";
  }

  @NotNull
  protected @NlsContexts.PopupTitle String getChooserTitle(@NotNull PsiElement sourceElement, @Nullable String name, int length, boolean finished) {
    return getChooserTitle(sourceElement, name, length);
  }

  @NotNull
  protected @NlsContexts.TabTitle String getFindUsagesTitle(@NotNull PsiElement sourceElement, String name, int length) {
    return getChooserTitle(sourceElement, name, length, true);
  }

  @NotNull
  protected abstract @NlsContexts.HintText String getNotFoundMessage(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file);

  @Nullable
  protected @NlsContexts.PopupAdvertisement String getAdText(PsiElement source, int length) {
    return null;
  }

  public interface AdditionalAction {
    @NlsActions.ActionText @NotNull String getText();

    Icon getIcon();

    void execute();
  }

  public static class GotoData {
    @NotNull public final PsiElement source;
    public PsiElement[] targets;
    public final List<AdditionalAction> additionalActions;
    public boolean isCanceled;

    private boolean hasDifferentNames;
    public BackgroundUpdaterTask listUpdaterTask;
    protected final Set<String> myNames;
    public Map<Object, TargetPresentation> presentations = new HashMap<>();

    public GotoData(@NotNull PsiElement source, PsiElement @NotNull [] targets, @NotNull List<AdditionalAction> additionalActions) {
      this.source = source;
      this.targets = targets;
      this.additionalActions = additionalActions;

      myNames = new HashSet<>();
      for (PsiElement target : targets) {
        if (target instanceof PsiNamedElement) {
          myNames.add(((PsiNamedElement)target).getName());
          if (myNames.size() > 1) break;
        }
      }

      hasDifferentNames = myNames.size() > 1;
    }

    public boolean hasDifferentNames() {
      return hasDifferentNames;
    }

    public boolean addTarget(final PsiElement element) {
      if (ArrayUtil.find(targets, element) > -1) return false;
      targets = ArrayUtil.append(targets, element);
      presentations.put(element, ReadAction.compute(() -> computePresentation(element, hasDifferentNames)));
      if (!hasDifferentNames && element instanceof PsiNamedElement) {
        final String name = ReadAction.compute(() -> ((PsiNamedElement)element).getName());
        myNames.add(name);
        hasDifferentNames = myNames.size() > 1;
      }
      return true;
    }

    public @NotNull TargetPresentation getPresentation(Object value) {
      return Objects.requireNonNull(presentations.get(value));
    }

    public @NotNull String getComparingObject(Object value) {
      TargetPresentation presentation = getPresentation(value);
      return Stream.of(
        presentation.getPresentableText(),
        presentation.getContainerText(),
        presentation.getLocationText()
      ).filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    @VisibleForTesting
    public void initPresentations() {
      presentations.putAll(computePresentationInBackground(source.getProject(), targets, hasDifferentNames));
    }
  }

  private static class DefaultPsiElementListCellRenderer extends PsiElementListCellRenderer {
    @Override
    public String getElementText(final PsiElement element) {
      if (element instanceof PsiNamedElement) {
        String name = ((PsiNamedElement)element).getName();
        if (name != null) {
          return name;
        }
      }
      PsiFile file = element.getContainingFile();
      if (file == null) {
        PsiUtilCore.ensureValid(element);
        LOG.error("No file for " + element.getClass());
        return element.toString();
      }
      return file.getName();
    }

    @Override
    protected String getContainerText(final PsiElement element, final String name) {
      if (element instanceof NavigationItem) {
        final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
        return presentation != null ? presentation.getLocationString():null;
      }

      return null;
    }
  }
}
