// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.model.Pointer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
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
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ExperimentalUI;
import com.intellij.usages.UsageView;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;
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
    String featureId = getFeatureUsedKey();
    if (featureId != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(featureId);
    }

    try {
      GotoData gotoData = getSourceAndTargetElements(editor, file);
      Consumer<JBPopup> showPopupProcedure = popup -> {
        if (!editor.isDisposed()) {
          popup.showInBestPositionFor(editor);
        }
      };
      if (gotoData != null) {
        show(project, editor, file, gotoData, showPopupProcedure);
      }
      else {
        chooseFromAmbiguousSources(editor, file, data -> show(project, editor, file, data, showPopupProcedure));
      }
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
        DumbModeBlockedFunctionality.GotoTarget);
    }
  }

  protected void chooseFromAmbiguousSources(Editor editor, PsiFile file, Consumer<? super GotoData> successCallback) { }

  protected abstract @NonNls @Nullable String getFeatureUsedKey();

  protected boolean useEditorFont() {
    return true;
  }

  protected abstract @Nullable GotoData getSourceAndTargetElements(Editor editor, PsiFile file);

  protected void show(@NotNull Project project,
                      @NotNull Editor editor,
                      @NotNull PsiFile file,
                      @NotNull GotoData gotoData,
                      @NotNull Consumer<? super JBPopup> showPopup) {
    if (gotoData.isCanceled) return;

    PsiElement[] targets = gotoData.targets;
    List<AdditionalAction> additionalActions = gotoData.additionalActions;

    if (targets.length == 0 && additionalActions.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, getNotFoundMessage(project, editor, file));
      return;
    }

    showNotEmpty(project, gotoData, showPopup);
  }

  void showNotEmpty(@NotNull Project project, @NotNull GotoData gotoData, @NotNull Consumer<? super JBPopup> showPopup) {
    PsiElement[] targets = gotoData.targets;
    List<AdditionalAction> additionalActions = gotoData.additionalActions;

    boolean finished = gotoData.listUpdaterTask == null || gotoData.listUpdaterTask.isFinished();
    if (targets.length == 1 && additionalActions.isEmpty() && finished) {
      navigateToElement(targets[0]);
      return;
    }

    final String name = ((NavigationItem)gotoData.source).getName();
    final String title = getChooserTitle(gotoData.source, name, targets.length, finished);

    gotoData.initPresentations();
    List<ItemWithPresentation> allElements = new ArrayList<>(targets.length + additionalActions.size());
    allElements.addAll(gotoData.getItems());
    if (shouldSortTargets()) {
      allElements.sort(createComparator(gotoData));
    }
    allElements.addAll(ContainerUtil.map(additionalActions, action -> new ItemWithPresentation(action,
                                                                                               TargetPresentation.builder(action.getText())
                                                                                                 .icon(action.getIcon()).presentation())));

    final IPopupChooserBuilder<ItemWithPresentation> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(allElements);
    final Ref<UsageView> usageView = new Ref<>();
    builder.setNamerForFiltering(item -> {
      if (item.getItem() instanceof AdditionalAction) {
        return ((AdditionalAction)item.getItem()).getText();
      }
      return item.getPresentation().getPresentableText();
    }).setTitle(title);
    if (useEditorFont()) {
      builder.setFont(EditorUtil.getEditorFont());
    }
    var renderer = new GotoTargetRendererNew(o -> ((ItemWithPresentation)o).getPresentation());
    builder.setRenderer(renderer).
      setItemsChosenCallback(selectedElements -> {
        for (ItemWithPresentation element : selectedElements) {
          if (element.getItem() instanceof AdditionalAction) {
            ((AdditionalAction)element.getItem()).execute();
          }
          else {
            navigate(project, element, navigatable -> navigateToElement(navigatable));
          }
        }
      }).
      withHintUpdateSupply().
      setMovable(true).
      setCancelCallback(() -> {
        final BackgroundUpdaterTaskBase<ItemWithPresentation> task = gotoData.listUpdaterTask;
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
      if (!ExperimentalUI.isNewUI()) {
        pane.setBorder(null);
      }
      pane.setViewportBorder(null);
    }

    if (gotoData.listUpdaterTask != null) {
      Alarm alarm = new Alarm(popup);
      alarm.addRequest(() -> showPopup.accept(popup), 300);
      gotoData.listUpdaterTask.init(popup, builder.getBackgroundUpdater(), usageView);
      ProgressManager.getInstance().run(gotoData.listUpdaterTask);
    }
    else {
      showPopup.accept(popup);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      popup.closeOk(null);
    }
  }

  public static void navigate(@NotNull Project project, ItemWithPresentation element, Consumer<Navigatable> navigator) {
    Navigatable nav;
    if (element.getItem() instanceof Navigatable) {
      nav = (Navigatable)element.getItem();
    }
    else {
      nav = ActionUtil.underModalProgress(project, IdeBundle.message("progress.title.preparing.navigation"),
                                          () -> {
                                            PsiElement psiElement = ((SmartPsiElementPointer<?>)element.getItem()).getElement();
                                            return psiElement == null ? null : EditSourceUtil.getDescriptor(psiElement);
                                          });
    }
    try {
      if (nav != null && nav.canNavigate()) {
        navigator.accept(nav);
      }
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("notification.navigation.is.not.available.while.indexing"),
        DumbModeBlockedFunctionality.GotoTarget);
    }
  }

  protected @NotNull Comparator<ItemWithPresentation> createComparator(@NotNull GotoData gotoData) {
    return Comparator.comparing(gotoData::getComparingObject);
  }

  public static List<ItemWithPresentation> computePresentationInBackground(
    @NotNull Project project,
    PsiElement @NotNull [] targets,
    boolean hasDifferentNames
  ) {
    SmartPointerManager manager = SmartPointerManager.getInstance(project);
    return ActionUtil.underModalProgress(project, CodeInsightBundle.message("progress.title.preparing.result"),
                                         () -> ContainerUtil.map(targets, element -> new ItemWithPresentation(
                                           manager.createSmartPsiElementPointer(element),
                                           computePresentation(element, hasDifferentNames))));
  }

  public static @NotNull TargetPresentation computePresentation(@NotNull PsiElement element, boolean hasDifferentNames) {
    TargetPresentation presentation = GotoTargetPresentationProvider.getTargetPresentationFromProviders(element, hasDifferentNames);
    if (presentation != null) return presentation;
    TargetPresentation renderer = getTargetPresentationFromRenderers(element, hasDifferentNames);
    if (renderer != null) return renderer;
    return ourDefaultTargetElementRenderer.computePresentation(element);
  }

  private static @Nullable TargetPresentation getTargetPresentationFromRenderers(@NotNull PsiElement element, boolean hasDifferentNames) {
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
    if (descriptor == null) return false;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-339117, EA-842843")) {
      if (!descriptor.canNavigate()) return false;
    }
    navigateToElement(descriptor);
    return true;
  }

  protected void navigateToElement(@NotNull Navigatable descriptor) {
    descriptor.navigate(true);
  }

  protected boolean shouldSortTargets() {
    return true;
  }


  /**
   * @deprecated use getChooserTitle(PsiElement, String, int, boolean) instead
   */
  @Deprecated(forRemoval = true)
  protected @NotNull @NlsContexts.PopupTitle String getChooserTitle(PsiElement sourceElement, String name, int length) {
    LOG.warn("Please override getChooserTitle(PsiElement, String, int, boolean) instead");
    return "";
  }

  protected @NotNull @NlsContexts.PopupTitle String getChooserTitle(@NotNull PsiElement sourceElement, @Nullable String name, int length, boolean finished) {
    return getChooserTitle(sourceElement, name, length);
  }

  protected @NotNull @NlsContexts.TabTitle String getFindUsagesTitle(@NotNull PsiElement sourceElement, String name, int length) {
    return getChooserTitle(sourceElement, name, length, true);
  }

  protected abstract @NotNull @NlsContexts.HintText String getNotFoundMessage(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file);

  protected @Nullable @NlsContexts.PopupAdvertisement String getAdText(PsiElement source, int length) {
    return null;
  }

  public interface AdditionalAction {
    @NlsActions.ActionText @NotNull String getText();

    Icon getIcon();

    void execute();
  }

  public static final class GotoData {
    public final @NotNull PsiElement source;
    public PsiElement[] targets;
    public final List<AdditionalAction> additionalActions;
    public boolean isCanceled;

    private boolean hasDifferentNames;
    public BackgroundUpdaterTaskBase<ItemWithPresentation> listUpdaterTask;
    private final Set<String> myNames;
    private List<ItemWithPresentation> myItems;

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

    public ItemWithPresentation addTarget(final PsiElement element) {
      if (ArrayUtil.find(targets, element) > -1) return null;

      if (!hasDifferentNames && element instanceof PsiNamedElement) {
        final String name = ReadAction.compute(() -> ((PsiNamedElement)element).getName());
        myNames.add(name);
        hasDifferentNames = myNames.size() > 1;
        if (hasDifferentNames) {
          for (ItemWithPresentation item : myItems) {
            if (item.getItem() instanceof Pointer<?>) {
              ReadAction.run(() -> {
                Object o = ((Pointer<?>)item.getItem()).dereference();
                if (o instanceof PsiElement) {
                  item.setPresentation(computePresentation((PsiElement)o, hasDifferentNames));
                }
              });
            }
          }
        }
      }

      targets = ArrayUtil.append(targets, element);
      return initPresentation(element, hasDifferentNames);
    }

    private static ItemWithPresentation initPresentation(PsiElement target, boolean hasDifferentNames) {
      Pointer<PsiElement> pointer = ReadAction.compute(() -> SmartPointerManager.createPointer(target));
      TargetPresentation presentation = ReadAction.compute(() -> computePresentation(target, hasDifferentNames));
      return new ItemWithPresentation(pointer, presentation);
    }

    public @NotNull String getComparingObject(ItemWithPresentation value) {
      TargetPresentation presentation = value.getPresentation();
      return Stream.of(
        presentation.getPresentableText(),
        presentation.getContainerText(),
        presentation.getLocationText()
      ).filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    @VisibleForTesting
    public void initPresentations() {
      myItems = computePresentationInBackground(source.getProject(), targets, hasDifferentNames);
    }

    public List<ItemWithPresentation> getItems() {
      return myItems;
    }
  }

  private static final class DefaultPsiElementListCellRenderer extends PsiElementListCellRenderer {
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
