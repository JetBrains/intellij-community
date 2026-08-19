// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.TypePresentationService;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.NotNullFactory;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ConstantFunction;
import com.intellij.util.NotNullFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.Icon;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * DOM-specific builder for {@link GutterIconRenderer}
 * and {@link com.intellij.codeInsight.daemon.LineMarkerInfo}.
 *
 * <p>This builder is frequently used to produce a {@link com.intellij.codeInsight.daemon.LineMarkerInfo},
 * which the daemon caches per file and keeps far longer than a single highlighting pass. For that reason
 * the targets must not be resolved eagerly when they are (or contain) PSI elements: an eagerly stored PSI
 * target is pinned inside the cached marker and may become stale after edits/reparse (a PSI leak).
 * When targets are PSI/DOM elements, prefer {@link #setTargets(NotNullLazyValue)} so they are recomputed
 * on demand instead of being retained. See IDEA-198837 for the motivating fix.
 *
 * <p><strong>A lazy supplier must not capture PSI either.</strong> It runs after the pass that created the
 * marker — usually when the gutter is clicked — so capturing the element the targets are derived from both
 * defeats the point (the capture is what retains PSI) and dereferences an element that may be gone by then.
 * Capture something that survives a reparse and re-derive inside the supplier, yielding no targets when it
 * no longer resolves:
 * <ul>
 *   <li>PSI: a {@link SmartPsiElementPointer}. Note that for elements which are not backed
 *       by an AST node — light elements such as the ones UAST returns from {@code getJavaPsi()} on
 *       non-Java languages — a pointer degrades to a hard reference, so anchor the {@code sourcePsi} instead.</li>
 *   <li>DOM: a {@link com.intellij.util.xml.DomAnchor}. A {@code DomElement} holds a hard reference to its
 *       {@code XmlElement}, so it is never safe to capture. Re-check the type after retrieving it:
 *       {@code retrieveDomElement()} casts unchecked, and a renamed tag may map to a different DOM element.</li>
 *   <li>JAM: a JAM element holds its {@code PsiAnnotation} through {@code PsiElementRef.Real}, so it is a reference
 *       that can be invalidated as well; anchor the annotation and look the JAM element up again.</li>
 * </ul>
 * See IDEA-392318 for what happens otherwise.
 *
 * <p><strong>What going lazy changes besides the retention.</strong> The targets are unknown while the marker is being
 * built, so:
 * <ul>
 *   <li>the builder cannot tell whether there are any, so the icon always becomes a navigate action. Clicking one that
 *       resolves to nothing shows the text given to {@link #setEmptyPopupText} — set it, or the click does nothing at
 *       all;</li>
 *   <li>the tooltip is no longer generated from the target names, so {@link #setTooltipText} is required;</li>
 *   <li>the resolved collection is memoized inside the renderer, so laziness postpones the retention rather than
 *       removing it: once something has asked for the targets, they are held until the next pass replaces the marker;</li>
 *   <li>comparing two renderers resolves their targets ({@code NavigationGutterIconRenderer.equals}). For a gutter
 *       installed through {@link #createGutterIcon(AnnotationHolder, PsiElement)} that happens inside the pass, because
 *       {@code HighlightInfo.attributesEqual} compares gutter renderers — so an expensive computation stays expensive
 *       there, and the memo above is populated straight away.</li>
 * </ul>
 */
public class NavigationGutterIconBuilder<T> {
  private static final @NonNls String PATTERN = "&nbsp;&nbsp;&nbsp;&nbsp;{0}";
  protected static final NotNullFunction<PsiElement,Collection<? extends PsiElement>> DEFAULT_PSI_CONVERTOR =
    ContainerUtil::createMaybeSingletonList;

  protected final Icon myIcon;
  private final NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> myConverter;

  protected NotNullLazyValue<Collection<? extends T>> myTargets;
  private boolean myLazy;
  protected @Tooltip String myTooltipText;
  protected @PopupTitle String myPopupTitle;
  protected @PopupContent String myEmptyText;
  private @PopupTitle String myTooltipTitle;
  protected GutterIconRenderer.Alignment myAlignment = GutterIconRenderer.Alignment.CENTER;
  private Computable<PsiElementListCellRenderer<?>> myCellRenderer;
  private @NotNull Function<? super T, String> myNamer = obj -> TypePresentationService.getService().getObjectName(obj);
  private final NotNullFunction<? super T, ? extends Collection<? extends GotoRelatedItem>> myGotoRelatedItemProvider;
  protected static final NotNullFunction<PsiElement, Collection<? extends GotoRelatedItem>> PSI_GOTO_RELATED_ITEM_PROVIDER =
    dom -> List.of(new GotoRelatedItem(dom, InspectionsBundle.message("xml.goto.group")));
  private @NotNull Supplier<? extends PsiTargetPresentationRenderer<PsiElement>> myTargetRenderer;

  protected NavigationGutterIconBuilder(final @NotNull Icon icon, @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter) {
    this(icon, converter, null);
  }

  protected NavigationGutterIconBuilder(final @NotNull Icon icon,
                                        @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter,
                                        final @Nullable NotNullFunction<? super T, ? extends Collection<? extends GotoRelatedItem>> gotoRelatedItemProvider) {
    myIcon = icon;
    myConverter = converter;
    myGotoRelatedItemProvider = gotoRelatedItemProvider;
  }

  public static @NotNull NavigationGutterIconBuilder<PsiElement> create(@NotNull Icon icon) {
    return create(icon, DEFAULT_PSI_CONVERTOR, PSI_GOTO_RELATED_ITEM_PROVIDER);
  }

  public static @NotNull NavigationGutterIconBuilder<PsiElement> create(@NotNull Icon icon, @NlsContexts.Separator String navigationGroup) {
    return create(icon, DEFAULT_PSI_CONVERTOR, element -> List.of(new GotoRelatedItem(element, navigationGroup)));
  }

  public static @NotNull <T> NavigationGutterIconBuilder<T> create(final @NotNull Icon icon,
                                                                   @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter) {
    return create(icon, converter, null);
  }

  public static @NotNull <T> NavigationGutterIconBuilder<T> create(final @NotNull Icon icon,
                                                                   @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter,
                                                                   final @Nullable NotNullFunction<? super T, ? extends Collection<? extends GotoRelatedItem>> gotoRelatedItemProvider) {
    return new NavigationGutterIconBuilder<>(icon, converter, gotoRelatedItemProvider);
  }

  /**
   * Eager convenience overload: the target is stored as a constant value and retained by the builder.
   * If the target is a PSI element and this builder produces a cached {@link com.intellij.codeInsight.daemon.LineMarkerInfo},
   * prefer {@link #setTargets(NotNullLazyValue)} to avoid retaining stale PSI.
   *
   * @see #setTargets(NotNullLazyValue)
   */
  public @NotNull NavigationGutterIconBuilder<T> setTarget(@Nullable T target) {
    return setTargets(ContainerUtil.createMaybeSingletonList(target));
  }

  /**
   * Eager convenience overload: the targets are stored as a constant value and retained by the builder.
   * If the targets are PSI elements and this builder produces a cached {@link com.intellij.codeInsight.daemon.LineMarkerInfo},
   * prefer {@link #setTargets(NotNullLazyValue)} to avoid retaining stale PSI.
   *
   * @see #setTargets(NotNullLazyValue)
   */
  @SafeVarargs
  public final @NotNull NavigationGutterIconBuilder<T> setTargets(T @NotNull ... targets) {
    return setTargets(Arrays.asList(targets));
  }

  /**
   * Recommended overload for PSI/DOM targets: the collection is computed lazily and not retained by the
   * builder, so the resulting {@link com.intellij.codeInsight.daemon.LineMarkerInfo} does not pin resolved
   * PSI elements. Use this instead of {@link #setTargets(Collection)} whenever the targets are (or contain)
   * PSI elements.
   * <p>
   * The supplier runs long after the pass that created the marker, so it must not capture the element the
   * targets are derived from — see the class javadoc for what to capture instead. Capturing it would keep
   * retaining PSI and would throw on a gutter click once that element is stale.
   * <p>
   * It also runs whenever the gutter happens to be used, which can be in dumb mode: handle
   * {@link com.intellij.openapi.project.IndexNotReadyException} rather than letting it out.
   * <p>
   * See the class javadoc for what else changes once the targets are lazy — an always-clickable icon, no generated
   * tooltip, and a memo that keeps the resolved targets afterwards.
   */
  public @NotNull NavigationGutterIconBuilder<T> setTargets(final @NotNull NotNullLazyValue<Collection<? extends T>> targets) {
    myTargets = targets;
    myLazy = true;
    return this;
  }

  /**
   * Eagerly retains the given targets (wrapped in a constant {@link NotNullLazyValue}). Because a
   * {@link com.intellij.codeInsight.daemon.LineMarkerInfo} produced via {@code createLineMarkerInfo} is
   * cached by the daemon, passing already-resolved PSI elements here pins them and risks stale PSI after
   * edits/reparse (a PSI leak). For PSI/DOM targets use {@link #setTargets(NotNullLazyValue)} instead so
   * they are recomputed on demand. See IDEA-198837.
   */
  public @NotNull NavigationGutterIconBuilder<T> setTargets(final @NotNull Collection<? extends T> targets) {
    if (ContainerUtil.containsIdentity(targets, null)) {
      throw new IllegalArgumentException("Must not pass collection with null target but got: " + targets);
    }
    myTargets = NotNullLazyValue.createConstantValue(targets);
    return this;
  }

  public @NotNull NavigationGutterIconBuilder<T> setTooltipText(@NotNull @Tooltip String tooltipText) {
    myTooltipText = tooltipText;
    return this;
  }

  public @NotNull NavigationGutterIconBuilder<T> setAlignment(final @NotNull GutterIconRenderer.Alignment alignment) {
    myAlignment = alignment;
    return this;
  }

  public @NotNull NavigationGutterIconBuilder<T> setPopupTitle(@NotNull @PopupTitle String popupTitle) {
    myPopupTitle = popupTitle;
    return this;
  }

  public @NotNull NavigationGutterIconBuilder<T> setEmptyPopupText(@NotNull @PopupContent String emptyText) {
    myEmptyText = emptyText;
    return this;
  }

  public @NotNull NavigationGutterIconBuilder<T> setTooltipTitle(final @NotNull @PopupTitle String tooltipTitle) {
    myTooltipTitle = tooltipTitle;
    return this;
  }

  public @NotNull NavigationGutterIconBuilder<T> setNamer(@NotNull NullableFunction<? super T, String> namer) {
    myNamer = namer;
    return this;
  }

  /**
   * This method may lead to a deadlock when used from pooled thread, e.g., from
   * {@link com.intellij.codeInsight.daemon.LineMarkerProvider#collectSlowLineMarkers(List, Collection)}.
   * {@link PsiElementListCellRenderer} is a UI component that acquires Swing tree lock on init.
   *
   * @deprecated Use {@link #setCellRenderer(Computable)} instead, then renderer will be instantiated lazily and from EDT
   */
  @Deprecated
  public @NotNull NavigationGutterIconBuilder<T> setCellRenderer(final @NotNull PsiElementListCellRenderer<?> cellRenderer) {
    myCellRenderer = new Computable.PredefinedValueComputable<>(cellRenderer);
    return this;
  }

  /**
   * @param cellRendererProvider list cell renderer for navigation popup
   */
  public @NotNull NavigationGutterIconBuilder<T> setCellRenderer(@NotNull Computable<PsiElementListCellRenderer<?>> cellRendererProvider) {
    myCellRenderer = cellRendererProvider;
    return this;
  }

  public @NotNull NavigationGutterIconBuilder<T> setTargetRenderer(@NotNull Supplier<? extends PsiTargetPresentationRenderer<PsiElement>> cellRendererProvider) {
    myTargetRenderer = cellRendererProvider;
    return this;
  }

  /**
   * @deprecated Use {{@link #createGutterIcon(AnnotationHolder, PsiElement)}} instead
   */
  @Deprecated(forRemoval = true)
  public @Nullable Annotation install(@NotNull AnnotationHolder holder, @Nullable PsiElement element) {
    if (!myLazy && myTargets.getValue().isEmpty() || element == null) return null;
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .gutterIconRenderer(createGutterIconRenderer(element.getProject(), null))
      .create();
    return null;
  }

  public void createGutterIcon(@NotNull AnnotationHolder holder, @Nullable PsiElement element) {
    if (!myLazy && myTargets.getValue().isEmpty() || element == null) return;

    NavigationGutterIconRenderer renderer = createGutterIconRenderer(element.getProject(), null);

    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(element)
      .gutterIconRenderer(renderer)
      .create();
  }

  public @NotNull RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element) {
    NavigationGutterIconRenderer renderer = createGutterIconRenderer(element.getProject(), null);
    return createLineMarkerInfo(element, renderer.isNavigateAction() ? renderer : null);
  }

  public @NotNull RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element,
                                                                             @Nullable GutterIconNavigationHandler<PsiElement> navigationHandler) {
    NavigationGutterIconRenderer renderer = createGutterIconRenderer(element.getProject(), navigationHandler);
    String tooltip = renderer.getTooltipText();
    return new RelatedItemLineMarkerInfo<>(
      element,
      element.getTextRange(),
      renderer.getIcon(),
      tooltip == null ? null : new ConstantFunction<>(tooltip),
      navigationHandler,
      renderer.getAlignment(),
      () -> computeGotoTargets());
  }

  protected @Unmodifiable @NotNull Collection<GotoRelatedItem> computeGotoTargets() {
    if (myTargets == null || myGotoRelatedItemProvider == null) return Collections.emptyList();
    NotNullFactory<Collection<? extends T>> factory = evaluateAndForget(myTargets);
    return ContainerUtil.concat(factory.create(), myGotoRelatedItemProvider);
  }

  private void checkBuilt() {
    assert myTargets != null : "Must have called .setTargets() before calling create()";
  }

  private static @NotNull <T> NotNullFactory<T> evaluateAndForget(@NotNull NotNullLazyValue<T> lazyValue) {
    final Ref<NotNullLazyValue<T>> ref = Ref.create(lazyValue);
    return new NotNullFactory<>() {
      volatile T value;

      @Override
      public @NotNull T create() {
        T result = value;
        if (result == null) {
          value = result = ref.get().getValue();
          ref.set(null);
        }
        return result;
      }
    };
  }

  public @NotNull NavigationGutterIconRenderer createGutterIconRenderer(@NotNull Project project,
                                                                           @Nullable GutterIconNavigationHandler<PsiElement> navigationHandler) {
    checkBuilt();

    NotNullFactory<Collection<? extends T>> factory = evaluateAndForget(myTargets);
    NotNullLazyValue<List<SmartPsiElementPointer<?>>> pointers = createPointersThunk(myLazy, project, factory, myConverter);

    final boolean empty = isEmpty();
    boolean newUI = ExperimentalUI.isNewUI() && !ApplicationManager.getApplication().isUnitTestMode();

    if (myTooltipText == null && !myLazy) {
      final SortedSet<String> names = new TreeSet<>();
      for (T t : myTargets.getValue()) {
        final String text = myNamer.apply(t);
        if (text != null) {
          names.add(newUI ? text : MessageFormat.format(PATTERN, text));
        }
      }
      @Nls StringBuilder sb = new StringBuilder("<html><body>");
      if (myTooltipTitle != null) {
        sb.append(myTooltipTitle).append("<br>");
      }
      for (String name : names) {
        sb.append(name).append("<br>");
      }
      sb.append("</body></html>");
      myTooltipText = sb.toString();
    }

    Computable<PsiElementListCellRenderer<?>> renderer =
      myCellRenderer == null ? DefaultPsiElementCellRenderer::new : myCellRenderer;
    NavigationGutterIconRenderer gutterIconRenderer = createGutterIconRenderer(pointers, renderer, empty, navigationHandler);
    gutterIconRenderer.setProject(project);
    gutterIconRenderer.setTargetRenderer(myTargetRenderer);
    return gutterIconRenderer;
  }

  protected @NotNull NavigationGutterIconRenderer createGutterIconRenderer(@NotNull NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> pointers,
                                                                           @NotNull Computable<? extends PsiElementListCellRenderer<?>> renderer,
                                                                           boolean empty,
                                                                           @Nullable GutterIconNavigationHandler<PsiElement> navigationHandler) {
    if (myLazy) {
      return createLazyGutterIconRenderer(pointers, renderer, empty, navigationHandler);
    }
    return new MyNavigationGutterIconRenderer(this, myAlignment, myIcon, myTooltipText, pointers, renderer, empty, navigationHandler);
  }

  private @NotNull NavigationGutterIconRenderer createLazyGutterIconRenderer(@NotNull NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> pointers,
                                                                             @NotNull Computable<? extends PsiElementListCellRenderer<?>> renderer,
                                                                             boolean empty,
                                                                             @Nullable GutterIconNavigationHandler<PsiElement> navigationHandler) {
    return new MyNavigationGutterIconRenderer(this, myAlignment, myIcon, myTooltipText, pointers, renderer, empty, true, navigationHandler);
  }

  private static @NotNull <T> NotNullLazyValue<List<SmartPsiElementPointer<?>>> createPointersThunk(boolean lazy,
                                                                                                    final Project project,
                                                                                                    final NotNullFactory<? extends Collection<? extends T>> targets,
                                                                                                    final NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter) {
    if (!lazy) {
      return NotNullLazyValue.createConstantValue(calcPsiTargets(project, targets.create(), converter));
    }

    return NotNullLazyValue.lazy(() -> calcPsiTargets(project, targets.create(), converter));
  }

  private static @NotNull <T> List<SmartPsiElementPointer<?>> calcPsiTargets(@NotNull Project project,
                                                                             @NotNull Collection<? extends T> targets,
                                                                             @NotNull NotNullFunction<? super T, ? extends Collection<? extends PsiElement>> converter) {
    SmartPointerManager manager = SmartPointerManager.getInstance(project);
    Set<PsiElement> elements = new HashSet<>();
    final List<SmartPsiElementPointer<?>> list = new ArrayList<>(targets.size());
    for (final T target : targets) {
      for (final PsiElement psiElement : converter.fun(target)) {
        if (psiElement == null) {
          throw new IllegalArgumentException(converter + " returned null element");
        }

        if (elements.add(psiElement) && psiElement.isValid()) {
          list.add(manager.createSmartPsiElementPointer(psiElement));
        }
      }
    }
    return list;
  }

  private boolean isEmpty() {
    if (myLazy) {
      return false;
    }
    Collection<? extends T> targets = myTargets.getValue();
    return ContainerUtil.all(targets, target -> myConverter.fun(target).isEmpty());
  }

  private static class MyNavigationGutterIconRenderer extends NavigationGutterIconRenderer {
    private final Alignment myAlignment;
    private final Icon myIcon;
    private final @Tooltip String myTooltipText;
    private final boolean myEmpty;

    MyNavigationGutterIconRenderer(@NotNull NavigationGutterIconBuilder<?> builder,
                                   @NotNull Alignment alignment,
                                   final Icon icon,
                                   final @Nullable @Tooltip String tooltipText,
                                   @NotNull NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> pointers,
                                   @NotNull Computable<? extends PsiElementListCellRenderer<?>> cellRenderer,
                                   boolean empty,
                                   @Nullable GutterIconNavigationHandler<PsiElement> navigationHandler) {
      super(builder.myPopupTitle, builder.myEmptyText, cellRenderer, pointers, false, navigationHandler);
      myAlignment = alignment;
      myIcon = icon;
      myTooltipText = tooltipText;
      myEmpty = empty;
    }

    MyNavigationGutterIconRenderer(@NotNull NavigationGutterIconBuilder<?> builder,
                                   @NotNull Alignment alignment,
                                   Icon icon,
                                   @Nullable @Tooltip String tooltipText,
                                   @NotNull NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> pointers,
                                   @NotNull Computable<? extends PsiElementListCellRenderer<?>> cellRenderer,
                                   boolean empty,
                                   boolean computeTargetsInBackground,
                                   @Nullable GutterIconNavigationHandler<PsiElement> navigationHandler) {
      super(builder.myPopupTitle, builder.myEmptyText, cellRenderer, pointers, computeTargetsInBackground, navigationHandler);
      myAlignment = alignment;
      myIcon = icon;
      myTooltipText = tooltipText;
      myEmpty = empty;
    }

    @Override
    public boolean isNavigateAction() {
      return !myEmpty;
    }

    @Override
    public @NotNull Icon getIcon() {
      return myIcon;
    }

    @Override
    public @Nullable String getTooltipText() {
      return myTooltipText;
    }

    @Override
    public @NotNull Alignment getAlignment() {
      return myAlignment;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!super.equals(o)) return false;

      final MyNavigationGutterIconRenderer that = (MyNavigationGutterIconRenderer)o;

      if (myAlignment != that.myAlignment) return false;
      if (!Objects.equals(myIcon, that.myIcon)) return false;
      return Objects.equals(myTooltipText, that.myTooltipText);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (myAlignment != null ? myAlignment.hashCode() : 0);
      result = 31 * result + (myIcon != null ? myIcon.hashCode() : 0);
      result = 31 * result + (myTooltipText != null ? myTooltipText.hashCode() : 0);
      return result;
    }
  }
}
