// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.SearchEverywherePsiRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.navigation.TargetPresentation;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class PSIPresentationBgRendererWrapper implements WeightedSearchEverywhereContributor<Object>, ScopeSupporting, AutoCompletionContributor{
  private final AbstractGotoSEContributor myDelegate;

  public PSIPresentationBgRendererWrapper(AbstractGotoSEContributor delegate) { myDelegate = delegate; }

  @Override
  public List<AutoCompletionCommand> getAutocompleteItems(String pattern, int caretPosition) {
    return myDelegate instanceof AutoCompletionContributor
           ? ((AutoCompletionContributor)myDelegate).getAutocompleteItems(pattern, caretPosition)
           : Collections.emptyList();
  }

  public static SearchEverywhereContributor<Object> wrapIfNecessary(AbstractGotoSEContributor delegate) {
    if (Registry.is("psi.element.list.cell.renderer.background")) {
      return new PSIPresentationBgRendererWrapper(delegate);
    }
    return delegate;
  }

  @Override
  public void fetchWeightedElements(@NotNull String pattern,
                                    @NotNull ProgressIndicator progressIndicator,
                                    @NotNull Processor<? super FoundItemDescriptor<Object>> consumer) {
    Function<PsiElement, TargetPresentation> calculator = createCalculator();
    myDelegate.fetchWeightedElements(pattern, progressIndicator, descriptor -> {
      FoundItemDescriptor<Object> presentationDescriptor = element2presentation(descriptor, calculator);
      return consumer.process(presentationDescriptor);
    });
  }

  private Function<PsiElement, TargetPresentation> createCalculator() {
    SearchEverywherePsiRenderer renderer = (SearchEverywherePsiRenderer)myDelegate.getElementsRenderer();
    return element -> renderer.computePresentation(element);
  }

  @Override
  public @NotNull ListCellRenderer<? super Object> getElementsRenderer() {

    return new WrapperRenderer((PsiElementListCellRenderer<?>)myDelegate.getElementsRenderer());
  }

  private static FoundItemDescriptor<Object> element2presentation(FoundItemDescriptor<Object> elementDescriptor,
                                                                  Function<PsiElement, TargetPresentation> presentationCalculator) {
    if (elementDescriptor.getItem() instanceof PsiElement) {
      PsiElement psi = (PsiElement)elementDescriptor.getItem();
      TargetPresentation presentation = presentationCalculator.apply(psi);
      return new FoundItemDescriptor<>(new PsiItemWithPresentation(psi, presentation), elementDescriptor.getWeight());
    }

    return elementDescriptor;
  }

  private static Object getItem(Object value) {
    return value instanceof PsiItemWithPresentation ? ((PsiItemWithPresentation)value).getItem() : value;
  }

  public static class PsiItemWithPresentation extends Pair<PsiElement, TargetPresentation> {
    /**
     * @param first
     * @param second
     * @see #create(Object, Object)
     */
    PsiItemWithPresentation(PsiElement first, TargetPresentation second) {
      super(first, second);
    }

    public PsiElement getItem() {
      return first;
    }

    public TargetPresentation getPresentation() {
      return second;
    }
  }

  private static class WrapperRenderer extends JPanel implements ListCellRenderer<Object> {

    private final PsiElementListCellRenderer<?> delegateRenderer;

    private WrapperRenderer(PsiElementListCellRenderer<?> renderer) {
      super(new SearchEverywherePsiRenderer.SELayout());
      delegateRenderer = renderer;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (!(value instanceof PsiItemWithPresentation)) return delegateRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      PsiItemWithPresentation itemAndPresentation = (PsiItemWithPresentation)value;

      TargetPresentation presentation = itemAndPresentation.getPresentation();
      PsiElementListCellRenderer.ItemMatchers matchers = getItemMatchers(list, itemAndPresentation);

      removeAll();

      Color bgColor = isSelected ? UIUtil.getListSelectionBackground(true) : presentation.getBackgroundColor();
      setBackground(bgColor);

      JLabel locationLabel = StringUtil.isNotEmpty(presentation.getLocationText())
                             ? new JLabel(presentation.getLocationText(), presentation.getLocationIcon(), SwingConstants.RIGHT)
                             : null;
      if (locationLabel != null) {
        locationLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        locationLabel.setForeground(isSelected ? UIUtil.getListSelectionForeground(true) : UIUtil.getInactiveTextColor());
        add(locationLabel, BorderLayout.EAST);
      }

      ColoredListCellRenderer<Object> leftRenderer = new ColoredListCellRenderer<>() {
        @Override
        protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
          setIcon(presentation.getIcon());
          SimpleTextAttributes nameAttributes = presentation.getPresentableTextAttributes() != null
                                                ? SimpleTextAttributes.fromTextAttributes(presentation.getPresentableTextAttributes())
                                                : new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, null);
          SpeedSearchUtil.appendColoredFragmentForMatcher(presentation.getPresentableText(), this, nameAttributes, matchers.nameMatcher, bgColor, selected);
          if (presentation.getContainerText() != null) {
            Insets listInsets = list.getInsets();
            Insets rendererInsets = getInsets();
            FontMetrics fm = list.getFontMetrics(list.getFont());
            int containerMaxWidth = list.getWidth() - listInsets.left - listInsets.right
                                    - rendererInsets.left - rendererInsets.right
                                    - getPreferredSize().width;
            if (locationLabel != null) containerMaxWidth -= locationLabel.getPreferredSize().width;

            @NlsSafe String containerText = cutContainerText(presentation.getContainerText(), containerMaxWidth, fm);
            SimpleTextAttributes containerAttributes = presentation.getContainerTextAttributes() != null
                                                       ? SimpleTextAttributes.fromTextAttributes(presentation.getContainerTextAttributes())
                                                       : SimpleTextAttributes.GRAYED_ATTRIBUTES;
            SpeedSearchUtil.appendColoredFragmentForMatcher(" " + containerText, this, containerAttributes, matchers.locationMatcher, bgColor, selected);
          }
        }
      };
      add(leftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus), BorderLayout.WEST);
      accessibleContext = leftRenderer.getAccessibleContext();
      return this;
    }

    protected PsiElementListCellRenderer.ItemMatchers getItemMatchers(@NotNull JList<?> list, @Nullable PsiItemWithPresentation value) {
      if (value == null) return new PsiElementListCellRenderer.ItemMatchers(null, null);
      return delegateRenderer.getItemMatchers(list, value.getItem());
    }

    @Nullable
    @Contract("!null, _, _ -> !null")
    private static String cutContainerText(@Nullable String text, int maxWidth, FontMetrics fm) {
      if (text == null) return null;

      if (text.startsWith("(") && text.endsWith(")")) {
        text = text.substring(1, text.length() - 1);
      }

      if (maxWidth < 0) return text;

      boolean in = text.startsWith("in ");
      if (in) text = text.substring(3);
      String left = in ? "in " : "";
      String adjustedText = left + text;

      int fullWidth = fm.stringWidth(adjustedText);
      if (fullWidth < maxWidth) return adjustedText;

      String separator = text.contains("/") ? "/" :
                         SystemInfo.isWindows && text.contains("\\") ? "\\" :
                         text.contains(".") ? "." :
                         text.contains("-") ? "-" : " ";
      LinkedList<String> parts = new LinkedList<>(StringUtil.split(text, separator));
      int index;
      while (parts.size() > 1) {
        index = parts.size() / 2 - 1;
        parts.remove(index);
        if (fm.stringWidth(left + StringUtil.join(parts, separator) + "...") < maxWidth) {
          parts.add(index, "...");
          return left + StringUtil.join(parts, separator);
        }
      }
      int adjustedWidth = Math.max(adjustedText.length() * maxWidth / fullWidth - 1, left.length() + 3);
      return StringUtil.trimMiddle(adjustedText, adjustedWidth);
    }
  }

  @Override
  @Nls
  @NotNull
  public String getGroupName() {
    return myDelegate.getGroupName();
  }

  @Override
  @Nls
  @NotNull
  public String getFullGroupName() {
    return myDelegate.getFullGroupName();
  }

  @Override
  public int getSortWeight() {
    return myDelegate.getSortWeight();
  }

  @Override
  @Nls
  @Nullable
  public String getAdvertisement() {
    return myDelegate.getAdvertisement();
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
  @NotNull
  public String getSearchProviderId() {
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
  @NotNull
  public String filterControlSymbols(@NotNull String pattern) {
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

  @Nullable
  @Override
  public Object getDataForItem(@NotNull Object element, @NotNull String dataId) {
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

  @Nullable
  public static PsiElement toPsi(Object o) {
    if (o instanceof PsiElement) return (PsiElement)o;
    if (o instanceof PsiItemWithPresentation) return ((PsiItemWithPresentation)o).getItem();
    if (o instanceof PsiElementNavigationItem) return ((PsiElementNavigationItem) o).getTargetElement();
    return null;
  }
}
