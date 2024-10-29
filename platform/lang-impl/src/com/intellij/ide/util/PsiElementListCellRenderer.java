// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.ui.UISettings;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.platform.backend.presentation.TargetPresentationBuilder;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.list.TargetPopup;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.TextWithIcon;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.intellij.openapi.vfs.newvfs.VfsPresentationUtil.getFileBackgroundColor;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * This class assumes a number of previously-working contracts (read action on EDT, model access on EDT),
 * which we are trying to get rid of because they slow down the rendering and user action response time.
 * <br/>
 * The renderer itself must not compute any data. The renderer must use data computed on a background thread.
 * The data must be computed before {@link #getListCellRendererComponent} is invoked. This data is <i>presentation</i>.
 * {@link TargetPresentation} can be used with the {@link TargetPopup#createTargetPresentationRenderer corresponding renderer},
 * or the presentation class/interface should be defined separately to allow customization on the presentation level (not on renderer level).
 * This approach is used in {@link TargetPopup#createTargetPopup(String, List, List, Consumer)},
 * other APIs/implementations are welcome to use the same approach as well.
 * </p>
 */
@Obsolete
@DirtyUI
// extends ListCellRenderer<Object> because it can render strings too
public abstract class PsiElementListCellRenderer<T extends PsiElement> extends JPanel implements ListCellRenderer<Object> {
  public static final Key<TargetPresentation> TARGET_PRESENTATION_KEY = Key.create("cell.target.presentation");
  private static final Logger LOG = Logger.getInstance(PsiElementListCellRenderer.class);
  private static final String LEFT = BorderLayout.WEST;
  private static final Pattern CONTAINER_PATTERN = Pattern.compile("(\\(in |\\()?([^)]*)(\\))?");
  private static final SimpleTextAttributes DEFAULT_ERROR_ATTRIBUTES
    = new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, NamedColorUtil.getInactiveTextColor(), JBColor.RED);

  protected int myRightComponentWidth;

  private final ListCellRenderer<PsiElement> myBackgroundRenderer;

  protected PsiElementListCellRenderer() {
    super(new BorderLayout());
    myBackgroundRenderer =
      Registry.is("psi.element.list.cell.renderer.background")
      ? new PsiElementBackgroundListCellRenderer(this)
      : null;
  }

  private final class MyAccessibleContext extends JPanel.AccessibleJPanel {
    @Override
    public String getAccessibleName() {
      LayoutManager lm = getLayout();
      assert lm instanceof BorderLayout;
      Component leftCellRendererComp = ((BorderLayout)lm).getLayoutComponent(LEFT);
      return leftCellRendererComp instanceof Accessible ?
             leftCellRendererComp.getAccessibleContext().getAccessibleName() : super.getAccessibleName();
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new MyAccessibleContext();
    }
    return accessibleContext;
  }

  public void setUsedInPopup(boolean value) {
    if (myBackgroundRenderer instanceof PsiElementBackgroundListCellRenderer psiElementRenderer) {
      psiElementRenderer.setUsedInPopup(value);
    }
  }

  public static final class ItemMatchers {
    public final @Nullable Matcher nameMatcher;
    public final @Nullable Matcher locationMatcher;

    public ItemMatchers(@Nullable Matcher nameMatcher, @Nullable Matcher locationMatcher) {
      this.nameMatcher = nameMatcher;
      this.locationMatcher = locationMatcher;
    }
  }

  private final class LeftRenderer extends ColoredListCellRenderer<Object> {

    private final ItemMatchers myMatchers;

    LeftRenderer(@NotNull ItemMatchers matchers) {
      myMatchers = matchers;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
      Color bgColor = UIUtil.getListBackground();
      Color color = list.getForeground();

      PsiElement target = PSIRenderingUtils.getPsiElement(value);
      VirtualFile vFile = PsiUtilCore.getVirtualFile(target);
      boolean isProblemFile = false;
      if (vFile != null) {
        Project project = target.getProject();
        isProblemFile = WolfTheProblemSolver.getInstance(project).isProblemFile(vFile);
        FileStatus status = FileStatusManager.getInstance(project).getStatus(vFile);
        color = status.getColor();

        Color fileBgColor = getFileBackgroundColor(project, vFile);
        bgColor = fileBgColor == null ? bgColor : fileBgColor;
      }

      if (value instanceof PsiElement) {
        //noinspection unchecked
        T element = (T)value;
        @NlsContexts.Label String name = ((PsiElement)value).isValid() ? getElementText(element) : "INVALID";

        TextAttributes attributes = element.isValid() ? getNavigationItemAttributes(value) : null;
        SimpleTextAttributes nameAttributes = attributes != null ? SimpleTextAttributes.fromTextAttributes(attributes) : null;
        if (nameAttributes == null) nameAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color);

        if (name == null) {
          LOG.error("Null name for PSI element " + element.getClass() + " (by " + PsiElementListCellRenderer.this + ")");
          name = LangBundle.message("label.unknown");
        }
        SpeedSearchUtil.appendColoredFragmentForMatcher(name, this, nameAttributes, myMatchers.nameMatcher, bgColor, selected);
        if (!element.isValid()) {
          append(" " + LangBundle.message("label.invalid"), SimpleTextAttributes.ERROR_ATTRIBUTES);
          return;
        }
        setIcon(PsiElementListCellRenderer.this.getIcon(element));

        FontMetrics fm = list.getFontMetrics(list.getFont());
        int maxWidth = list.getWidth() -
                       fm.stringWidth(name) -
                       16 - myRightComponentWidth - 20;
        String containerText = getContainerTextForLeftComponent(element, name, maxWidth, fm);
        if (containerText != null) {
          appendLocationText(selected, bgColor, isProblemFile, containerText);
        }
      }
      else if (!customizeNonPsiElementLeftRenderer(this, list, value, index, selected, hasFocus)) {
        setIcon(IconUtil.getEmptyIcon(false));
        @NlsSafe String text = value == null ? "" : value.toString();
        append(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
      }
      setBackground(selected ? UIUtil.getListSelectionBackground(true) : bgColor);
    }

    private void appendLocationText(boolean selected, Color bgColor, boolean isProblemFile, @Nls String containerText) {
      SimpleTextAttributes locationAttrs = SimpleTextAttributes.GRAYED_ATTRIBUTES;
      if (isProblemFile) {
        SimpleTextAttributes wavedAttributes = SimpleTextAttributes.merge(getErrorAttributes(), locationAttrs);
        java.util.regex.Matcher matcher = CONTAINER_PATTERN.matcher(containerText);
        if (matcher.matches()) {
          String prefix = matcher.group(1);
          SpeedSearchUtil.appendColoredFragmentForMatcher(" " + ObjectUtils.notNull(prefix, ""), this, locationAttrs, myMatchers.locationMatcher, bgColor, selected);

          String strippedContainerText = matcher.group(2);
          SpeedSearchUtil.appendColoredFragmentForMatcher(ObjectUtils.notNull(strippedContainerText, ""), this, wavedAttributes, myMatchers.locationMatcher, bgColor, selected);

          String suffix = matcher.group(3);
          if (suffix != null) {
            SpeedSearchUtil.appendColoredFragmentForMatcher(suffix, this, locationAttrs, myMatchers.locationMatcher, bgColor, selected);
          }
          return;
        }
        locationAttrs = wavedAttributes;
      }
      SpeedSearchUtil.appendColoredFragmentForMatcher(" " + containerText, this, locationAttrs, myMatchers.locationMatcher, bgColor, selected);
    }
  }

  protected @Nullable TextAttributes getNavigationItemAttributes(Object value) {
    return PSIRenderingUtils.getNavigationItemAttributesStatic(value);
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && value instanceof PsiElement) {
      putClientProperty(TARGET_PRESENTATION_KEY, computePresentation((PsiElement)value));
      return this;
    }
    else if (myBackgroundRenderer != null && value instanceof PsiElement) {
      //noinspection unchecked
      return myBackgroundRenderer.getListCellRendererComponent(list, (PsiElement)value, index, isSelected, cellHasFocus);
    }

    removeAll();
    myRightComponentWidth = 0;

    TextWithIcon itemLocation = getItemLocation(value);
    final JLabel locationComponent;
    final JPanel spacer;
    if (itemLocation == null) {
      locationComponent = null;
      spacer = null;
    }
    else {
      locationComponent = new JLabel(itemLocation.getText(), itemLocation.getIcon(), SwingConstants.RIGHT);
      locationComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, UIUtil.getListCellHPadding()));
      locationComponent.setHorizontalTextPosition(SwingConstants.LEFT);
      locationComponent.setForeground(isSelected ? NamedColorUtil.getListSelectionForeground(true) : NamedColorUtil.getInactiveTextColor());

      add(locationComponent, BorderLayout.EAST);
      spacer = new JPanel();
      spacer.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
      add(spacer, BorderLayout.CENTER);
      myRightComponentWidth = locationComponent.getPreferredSize().width;
      myRightComponentWidth += spacer.getPreferredSize().width;
    }

    ListCellRenderer<Object> leftRenderer = createLeftRenderer(list, value);
    Component result = leftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    final Component leftCellRendererComponent = result;
    add(leftCellRendererComponent, LEFT);
    final Color bg = isSelected ? UIUtil.getListSelectionBackground(true) : leftCellRendererComponent.getBackground();
    setBackground(bg);
    if (itemLocation != null) {
      locationComponent.setBackground(bg);
      spacer.setBackground(bg);
    }
    return this;
  }

  protected @NotNull ColoredListCellRenderer<Object> createLeftRenderer(JList<?> list, Object value) {
    return new LeftRenderer(value == null ? new ItemMatchers(null, null) : getItemMatchers(list, value));
  }

  protected @NotNull SimpleTextAttributes getErrorAttributes() {
    return DEFAULT_ERROR_ATTRIBUTES;
  }

  public @NotNull ItemMatchers getItemMatchers(@NotNull JList list, @NotNull Object value) {
    return new ItemMatchers(MatcherHolder.getAssociatedMatcher(list), null);
  }

  protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer,
                                                       JList list,
                                                       Object value,
                                                       int index,
                                                       boolean selected,
                                                       boolean hasFocus) {
    return false;
  }

  protected @Nullable TextWithIcon getItemLocation(Object value) {
    if (isGetRightCellRendererOverridden) {
      return ModuleRendererFactory.getTextWithIcon(getRightCellRenderer(value), value);
    }
    if (UISettings.getInstance().getShowIconInQuickNavigation()) {
      return getModuleTextWithIcon(value);
    }
    return null;
  }

  private final boolean isGetRightCellRendererOverridden = ReflectionUtil.getMethodDeclaringClass(
    getClass(), "getRightCellRenderer", Object.class
  ) != PsiElementListCellRenderer.class;

  /**
   * @deprecated override {@link #getItemLocation} instead
   */
  @Deprecated
  protected @Nullable DefaultListCellRenderer getRightCellRenderer(final Object value) {
    if (!UISettings.getInstance().getShowIconInQuickNavigation()) {
      return null;
    }
    final DefaultListCellRenderer renderer = ModuleRendererFactory.findInstance(value).getModuleRenderer();
    if (renderer instanceof PlatformModuleRendererFactory.PlatformModuleRenderer) {
      // it won't display any new information
      return null;
    }
    return renderer;
  }

  @ApiStatus.Internal
  public static @Nullable TextWithIcon getModuleTextWithIcon(Object value) {
    ModuleRendererFactory factory = ModuleRendererFactory.findInstance(value);
    if (factory instanceof PlatformModuleRendererFactory) {
      // it won't display any new information
      return null;
    }
    return factory.getModuleTextWithIcon(value);
  }

  public abstract @NlsSafe String getElementText(T element);

  protected abstract @Nullable @NlsSafe String getContainerText(T element, final String name);

  protected @Nullable @NlsSafe String getContainerTextForLeftComponent(T element, String name, int maxWidth, FontMetrics fm) {
    return getContainerText(element, name);
  }

  @Iconable.IconFlags
  protected int getIconFlags() {
    return 0;
  }

  protected Icon getIcon(PsiElement element) {
    return element.getIcon(getIconFlags());
  }

  public Comparator<T> getComparator() {
    //noinspection unchecked
    return Comparator.comparing(this::getComparingObject);
  }

  public @NotNull Comparable getComparingObject(T element) {
    return ReadAction.compute(() -> {
      String elementText = getElementText(element);
      String containerText = getContainerText(element, elementText);
      TextWithIcon moduleTextWithIcon = getModuleTextWithIcon(element);
      return (containerText == null ? elementText : elementText + " " + containerText) +
             (moduleTextWithIcon != null ? " " + moduleTextWithIcon.getText() : "");
    });
  }

  /**
   * @deprecated use {@link #installSpeedSearch(IPopupChooserBuilder)} instead
   */
  @Deprecated
  public void installSpeedSearch(PopupChooserBuilder<?> builder) {
    installSpeedSearch((IPopupChooserBuilder)builder);
  }

  public void installSpeedSearch(IPopupChooserBuilder builder) {
    installSpeedSearch(builder, false);
  }

  public void installSpeedSearch(@NotNull IPopupChooserBuilder builder, final boolean includeContainerText) {
    builder.setNamerForFiltering(o -> {
      if (o instanceof PsiElement) {
        final String elementText = getElementText((T)o);
        if (includeContainerText) {
          return elementText + " " + getContainerText((T)o, elementText);
        }
        return elementText;
      }
      return o.toString();
    });
  }

  @ApiStatus.Internal
  @RequiresReadLock
  public final @NotNull TargetPresentation computePresentation(@NotNull PsiElement element) {
    //noinspection unchecked
    return targetPresentation(
      (T)element,
      myRenderingInfo,
      this::getNavigationItemAttributes,
      this::getItemLocation,
      this::getErrorAttributes
    );
  }

  private final PsiElementRenderingInfo<T> myRenderingInfo = new PsiElementRenderingInfo<>() {
    @Override
    public @Nullable Icon getIcon(@NotNull T element) {
      return PsiElementListCellRenderer.this.getIcon(element);
    }

    @Override
    public @NotNull String getPresentableText(@NotNull T element) {
      String elementText = getElementText(element);
      if (elementText == null) {
        LOG.error("Null name for PSI element " + element.getClass() + " (by " + PsiElementListCellRenderer.this + ")");
        return LangBundle.message("label.unknown");
      }
      return elementText;
    }

    @Override
    public @Nullable String getContainerText(@NotNull T element) {
      return PsiElementListCellRenderer.this.getContainerText(element, getPresentableText(element));
    }
  };

  static <T extends PsiElement>
  @NotNull TargetPresentation targetPresentation(@NotNull T element, @NotNull PsiElementRenderingInfo<? super T> renderingInfo) {
    return targetPresentation(
      element,
      renderingInfo,
      PSIRenderingUtils::getNavigationItemAttributesStatic,
      PsiElementListCellRenderer::getModuleTextWithIcon,
      () -> DEFAULT_ERROR_ATTRIBUTES
    );
  }

  private static <T extends PsiElement>
  @NotNull TargetPresentation targetPresentation(
    @NotNull T element,
    @NotNull PsiElementRenderingInfo<? super T> renderingInfo,
    @NotNull Function<? super @NotNull T, ? extends @Nullable TextAttributes> presentableAttributesProvider,
    @NotNull Function<? super @NotNull T, ? extends @Nullable TextWithIcon> locationProvider,
    @NotNull Supplier<? extends @NotNull SimpleTextAttributes> errorAttributesSupplier
  ) {
    TargetPresentationBuilder builder = TargetPresentation.builder(renderingInfo.getPresentableText(element));
    builder = builder.icon(renderingInfo.getIcon(element));

    TextAttributes elementAttributes = presentableAttributesProvider.apply(element);
    VirtualFile vFile = PsiUtilCore.getVirtualFile(element);
    if (vFile == null) {
      builder = builder.presentableTextAttributes(elementAttributes);
    }
    else {
      Project project = element.getProject();
      TextAttributes presentableAttributes = elementAttributes;
      if (presentableAttributes == null) {
        Color color = FileStatusManager.getInstance(project).getStatus(vFile).getColor();
        if (color != null) {
          presentableAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color).toTextAttributes();
        }
      }
      if (WolfTheProblemSolver.getInstance(project).isProblemFile(vFile)) {
        presentableAttributes = TextAttributes.merge(errorAttributesSupplier.get().toTextAttributes(), presentableAttributes);
      }
      builder = builder.presentableTextAttributes(presentableAttributes);
      builder = builder.backgroundColor(getFileBackgroundColor(project, vFile));
    }

    String containerText = renderingInfo.getContainerText(element);
    if (containerText != null) {
      var matcher = CONTAINER_PATTERN.matcher(containerText);
      builder = builder.containerText(matcher.matches() ? matcher.group(2) : containerText);
    }

    TextWithIcon itemLocation = locationProvider.apply(element);
    if (itemLocation != null) {
      builder = builder.locationText(itemLocation.getText(), itemLocation.getIcon());
    }

    return builder.presentation();
  }
}
