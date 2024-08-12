// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.SearchEverywherePsiRenderer;
import com.intellij.ide.util.PSIRenderingUtils;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.render.RendererPanelsUtils;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.Processor;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class PSIPresentationBgRendererWrapper implements WeightedSearchEverywhereContributor<Object>, ScopeSupporting,
                                                               AutoCompletionContributor, PossibleSlowContributor, EssentialContributor,
                                                               SearchEverywhereExtendedInfoProvider, SearchEverywherePreviewProvider,
                                                               SearchEverywhereContributorWrapper {
  private static final Logger LOG = Logger.getInstance(PSIPresentationBgRendererWrapper.class);

  private final AbstractGotoSEContributor myDelegate;

  public PSIPresentationBgRendererWrapper(AbstractGotoSEContributor delegate) { myDelegate = delegate; }

  @Override
  public List<AutoCompletionCommand> getAutocompleteItems(String pattern, int caretPosition) {
    return myDelegate instanceof AutoCompletionContributor
           ? ((AutoCompletionContributor)myDelegate).getAutocompleteItems(pattern, caretPosition)
           : Collections.emptyList();
  }

  @Override
  public boolean isSlow() {
    return PossibleSlowContributor.checkSlow(myDelegate);
  }

  @Override
  public boolean isEssential() {
    return EssentialContributor.checkEssential(myDelegate);
  }

  @Override
  public @NotNull SearchEverywhereContributor<?> getEffectiveContributor() {
    return myDelegate;
  }

  public static WeightedSearchEverywhereContributor<Object> wrapIfNecessary(AbstractGotoSEContributor delegate) {
    if (Registry.is("psi.element.list.cell.renderer.background")) {
      return new PSIPresentationBgRendererWrapper(delegate);
    }
    return delegate;
  }

  @Override
  public void fetchWeightedElements(@NotNull String pattern,
                                    @NotNull ProgressIndicator progressIndicator,
                                    @NotNull Processor<? super FoundItemDescriptor<Object>> consumer) {
    Function<PsiElement, TargetPresentation> psiCalculator = createPSICalculator();
    ListCellRenderer<? super Object> delegateRenderer = myDelegate.getElementsRenderer();
    SearchEverywherePresentationProvider<? super Object> presentationProvider = (delegateRenderer instanceof SearchEverywherePresentationProvider)
                                                                        ? (SearchEverywherePresentationProvider<Object>)delegateRenderer
                                                                        : null;
    myDelegate.fetchWeightedElements(pattern, progressIndicator, descriptor -> {
      FoundItemDescriptor<Object> presentationDescriptor = element2presentation(descriptor, psiCalculator, presentationProvider);
      return consumer.process(presentationDescriptor);
    });
  }

  private static TargetPresentation convertPresentation(ItemPresentation presentation) {
    return TargetPresentation.builder(Objects.requireNonNullElse(presentation.getPresentableText(), ""))
      .icon(presentation.getIcon(true))
      .locationText(presentation.getLocationString())
      .presentation();
  }

  private Function<PsiElement, TargetPresentation> createPSICalculator() {
    SearchEverywherePsiRenderer renderer = (SearchEverywherePsiRenderer)myDelegate.getElementsRenderer();
    return element -> renderer.computePresentation(element);
  }

  @Override
  public @NotNull ListCellRenderer<? super Object> getElementsRenderer() {
    PsiElementListCellRenderer<?> renderer = (PsiElementListCellRenderer<?>)myDelegate.getElementsRenderer();
    return new WrapperRenderer((list, o) -> renderer.getItemMatchers(list, o));
  }

  private static FoundItemDescriptor<Object> element2presentation(FoundItemDescriptor<Object> elementDescriptor,
                                                           Function<? super PsiElement, ? extends TargetPresentation> psiPresentationCalculator,
                                                           @Nullable SearchEverywherePresentationProvider<Object> rendererPresentationProvider) {
    if (elementDescriptor.getItem() instanceof PsiItemWithSimilarity<?> itemWithSimilarity) {
      TargetPresentation presentation = calcPresentation(itemWithSimilarity.getValue(), psiPresentationCalculator, rendererPresentationProvider);
      PsiItemWithSimilarity<?> newItemWithSimilarity = new PsiItemWithSimilarity<>(itemWithSimilarity.getValue(), itemWithSimilarity.getSimilarityScore());
      return new FoundItemDescriptor<>(new ItemWithPresentation<>(newItemWithSimilarity, presentation), elementDescriptor.getWeight());
    }

    TargetPresentation presentation = calcPresentation(elementDescriptor.getItem(), psiPresentationCalculator, rendererPresentationProvider);

    if (elementDescriptor.getItem() instanceof PsiElement psi) {
      return new FoundItemDescriptor<>(new PsiItemWithPresentation(psi, presentation), elementDescriptor.getWeight());
    }

    if (elementDescriptor.getItem() instanceof PsiElementNavigationItem psiElementNavigationItem) {
      var realElement = psiElementNavigationItem.getTargetElement();
      return new FoundItemDescriptor<>(new PsiItemWithPresentation(realElement, presentation), elementDescriptor.getWeight());
    }

    return new FoundItemDescriptor<>(new ItemWithPresentation<>(elementDescriptor.getItem(), presentation), elementDescriptor.getWeight());
  }

  private static TargetPresentation calcPresentation(Object item, Function<? super PsiElement, ? extends TargetPresentation> psiPresentationCalculator,
                                                     @Nullable SearchEverywherePresentationProvider<Object> rendererPresentationProvider) {
    if (item instanceof PsiElement psi) {
      return psiPresentationCalculator.apply(psi);
    }

    if (item instanceof PsiElementNavigationItem psiElementNavigationItem) {
      var realElement = psiElementNavigationItem.getTargetElement();
      return psiPresentationCalculator.apply(realElement);
    }

    if (item instanceof NavigationItem navigationItem) {
      ItemPresentation itemPresentation = Objects.requireNonNull(navigationItem.getPresentation());
      return convertPresentation(itemPresentation);
    }

    if (item instanceof ItemPresentation itemPresentation) {
      return convertPresentation(itemPresentation);
    }

    if (rendererPresentationProvider != null) {
      return rendererPresentationProvider.getTargetPresentation(item);
    }

    LOG.error("Found items expected to be PsiItems or to have [com.intellij.navigation.ItemPresentation] field. But item [" + item.getClass() + "] is not");
    @NlsSafe String text = item.toString();
    return TargetPresentation.builder(text).icon(IconUtil.getEmptyIcon(false)).presentation();
  }

  @ApiStatus.Internal
  public static Object getItem(Object value) {
    if (value instanceof ItemWithPresentation<?> iwp) value = iwp.getItem();
    if (value instanceof PsiItemWithSimilarity<?> iws) value = iws.getValue();
    return value;
  }

  @ApiStatus.Internal
  public static class ItemWithPresentation<T> extends Pair<T, TargetPresentation> {
    /**
     * @see #create(Object, Object)
     */
    public ItemWithPresentation(T first, TargetPresentation second) {
      super(first, second);
    }

    public T getItem() {
      return first;
    }

    public TargetPresentation getPresentation() {
      return second;
    }
  }

  public static final class PsiItemWithPresentation extends ItemWithPresentation<PsiElement> {

    @ApiStatus.Internal
    public PsiItemWithPresentation(PsiElement first, TargetPresentation second) {
      super(first, second);
    }

  }

  private static final class WrapperRenderer extends JPanel implements ListCellRenderer<Object> {
    private final BiFunction<JList<?>, Object, PsiElementListCellRenderer.ItemMatchers> matchersSupplier;

    private WrapperRenderer(BiFunction<JList<?>, Object, PsiElementListCellRenderer.ItemMatchers> supplier) {
      super(new SearchEverywherePsiRenderer.SELayout());
      matchersSupplier = supplier;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (value instanceof PsiItemWithSimilarity<?> itemWithSimilarity) {
        return getListCellRendererComponent(list, itemWithSimilarity.getValue(), index, isSelected, cellHasFocus);
      }
      ItemWithPresentation<?> itemAndPresentation = (ItemWithPresentation<?>)value;

      TargetPresentation presentation = itemAndPresentation.getPresentation();
      PsiElementListCellRenderer.ItemMatchers matchers = getItemMatchers(list, itemAndPresentation);

      removeAll();

      Color bgColor = isSelected ? UIUtil.getListSelectionBackground(true) : presentation.getBackgroundColor();
      setBackground(bgColor);

      JLabel locationLabel;
      if (StringUtil.isNotEmpty(presentation.getLocationText())) {
        locationLabel = new JLabel(presentation.getLocationText(), presentation.getLocationIcon(), SwingConstants.RIGHT);
        locationLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        locationLabel.setIconTextGap(RendererPanelsUtils.getIconTextGap());
        locationLabel.setForeground(isSelected ? NamedColorUtil.getListSelectionForeground(true) : NamedColorUtil.getInactiveTextColor());
        add(locationLabel, BorderLayout.EAST);
      }
      else {
        locationLabel = null;
      }

      ColoredListCellRenderer<Object> leftRenderer = new ColoredListCellRenderer<>() {
        @Override
        protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
          //noinspection UseDPIAwareInsets
          setIpad(new Insets(0, 0, 0, getIpad().right)); // Border of top panel is used for around insets of renderer
          setIcon(presentation.getIcon());
          setIconTextGap(RendererPanelsUtils.getIconTextGap());
          SimpleTextAttributes nameAttributes = presentation.getPresentableTextAttributes() != null
                                                ? SimpleTextAttributes.fromTextAttributes(presentation.getPresentableTextAttributes())
                                                : new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, null);
          SpeedSearchUtil.appendColoredFragmentForMatcher(presentation.getPresentableText(), this, nameAttributes, matchers.nameMatcher,
                                                          bgColor, selected);
          if (presentation.getContainerText() != null) {
            Insets listInsets = list.getInsets();
            Insets rendererInsets = getInsets();
            FontMetrics fm = list.getFontMetrics(list.getFont());
            int containerMaxWidth = list.getWidth() - listInsets.left - listInsets.right
                                    - rendererInsets.left - rendererInsets.right
                                    - getPreferredSize().width;
            if (locationLabel != null) containerMaxWidth -= locationLabel.getPreferredSize().width;

            @NlsSafe String containerText = PSIRenderingUtils.cutContainerText(presentation.getContainerText(), containerMaxWidth, fm);
            SimpleTextAttributes containerAttributes = presentation.getContainerTextAttributes() != null
                                                       ? SimpleTextAttributes.fromTextAttributes(presentation.getContainerTextAttributes())
                                                       : SimpleTextAttributes.GRAYED_ATTRIBUTES;
            SpeedSearchUtil.appendColoredFragmentForMatcher(" " + containerText, this, containerAttributes, matchers.locationMatcher,
                                                            bgColor, selected);
          }
        }
      };
      add(leftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus), BorderLayout.WEST);
      accessibleContext = leftRenderer.getAccessibleContext();
      return this;
    }

    private PsiElementListCellRenderer.ItemMatchers getItemMatchers(@NotNull JList<?> list, @Nullable ItemWithPresentation<?> value) {
      if (value == null) return new PsiElementListCellRenderer.ItemMatchers(null, null);
      return matchersSupplier.apply(list, value.getItem());
    }
  }

  @Override
  public @Nls @NotNull String getGroupName() {
    return myDelegate.getGroupName();
  }

  @Override
  public @Nls @NotNull String getFullGroupName() {
    return myDelegate.getFullGroupName();
  }

  @Override
  public int getSortWeight() {
    return myDelegate.getSortWeight();
  }

  @Override
  public @Nls @Nullable String getAdvertisement() {
    return myDelegate.getAdvertisement();
  }

  @Override
  public @Nullable ExtendedInfo createExtendedInfo() {
    return myDelegate.createExtendedInfo();
  }

  @Override
  public @NotNull List<AnAction> getActions(@NotNull Runnable onChanged) {
    return myDelegate.getActions(onChanged);
  }

  @Override
  public boolean isEmptyPatternSupported() {
    return myDelegate.isEmptyPatternSupported();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDelegate);
  }

  @Override
  public @NotNull String getSearchProviderId() {
    return myDelegate.getSearchProviderId();
  }

  @Override
  public boolean isShownInSeparateTab() {
    return myDelegate.isShownInSeparateTab();
  }

  @Override
  public ScopeDescriptor getScope() {
    return myDelegate.getScope();
  }

  @Override
  public void setScope(ScopeDescriptor scope) {
    myDelegate.setScope(scope);
  }

  @Override
  public List<ScopeDescriptor> getSupportedScopes() {
    return myDelegate.getSupportedScopes();
  }

  @Override
  public @NotNull List<SearchEverywhereCommandInfo> getSupportedCommands() {
    return myDelegate.getSupportedCommands();
  }

  @Override
  public @NotNull String filterControlSymbols(@NotNull String pattern) {
    return myDelegate.filterControlSymbols(pattern);
  }


  @Override
  public boolean showInFindResults() {
    return myDelegate.showInFindResults();
  }

  @Override
  public boolean processSelectedItem(@NotNull Object selected,
                                     int modifiers,
                                     @NotNull String searchText) {
    return myDelegate.processSelectedItem(getItem(selected), modifiers, searchText);
  }

  @Override
  public @Nullable Object getDataForItem(@NotNull Object element, @NotNull String dataId) {
    return myDelegate.getDataForItem(getItem(element), dataId);
  }

  @Override
  public boolean isMultiSelectionSupported() {
    return myDelegate.isMultiSelectionSupported();
  }

  @Override
  public boolean isDumbAware() {
    return myDelegate.isDumbAware();
  }

  @Override
  public int getElementPriority(@NotNull Object element,
                                @NotNull String searchPattern) {
    return myDelegate.getElementPriority(getItem(element), searchPattern);
  }

  public static @Nullable PsiElement toPsi(Object o) {
    if (o instanceof PsiItemWithSimilarity<?> itemWithSimilarity) return toPsi(itemWithSimilarity.getValue());
    if (o instanceof PsiElement) return (PsiElement)o;
    if (o instanceof ItemWithPresentation<?> wp) return toPsi(wp.getItem());
    if (o instanceof PsiElementNavigationItem en) return en.getTargetElement();
    return null;
  }

  public AbstractGotoSEContributor getDelegate() {
    return myDelegate;
  }
}
