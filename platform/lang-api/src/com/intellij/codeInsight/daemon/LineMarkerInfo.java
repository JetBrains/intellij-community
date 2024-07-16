// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.Supplier;

public class LineMarkerInfo<T extends PsiElement> {
  private static final Logger LOG = Logger.getInstance(LineMarkerInfo.class);

  protected final Icon myIcon;
  private final @NotNull SmartPsiElementPointer<? extends T> elementRef;
  public final int startOffset;
  public final int endOffset;
  public Color separatorColor;
  public SeparatorPlacement separatorPlacement;
  public volatile RangeHighlighter highlighter;

  public int updatePass;
  private final Function<? super T, @NlsContexts.Tooltip String> myTooltipProvider;
  private final Supplier<@Nls @NotNull String> myAccessibleNameProvider;
  private AnAction myNavigateAction;
  private final @NotNull GutterIconRenderer.Alignment myIconAlignment;
  private final GutterIconNavigationHandler<T> myNavigationHandler;

  /**
   * Creates a line marker info for the element.
   * See {@link LineMarkerProvider#getLineMarkerInfo(PsiElement)} javadoc
   * for specific quirks on which elements to use for line markers.
   *
   * @param element the element for which the line marker is created.
   * @param range the range (relative to beginning of file) with which the marker is associated
   * @param icon the icon to show in the gutter for the line marker
   * @param tooltipProvider the callback to calculate the tooltip for the gutter icon
   * @param navHandler the handler executed when the gutter icon is clicked
   * @param accessibleNameProvider the callback to provide a localized accessible name for the icon (for screen readers)
   */
  public LineMarkerInfo(@NotNull T element,
                        @NotNull TextRange range,
                        @NotNull Icon icon,
                        @Nullable Function<? super T, @NlsContexts.Tooltip String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler,
                        @NotNull GutterIconRenderer.Alignment alignment,
                        @NotNull Supplier<@NotNull @Nls String> accessibleNameProvider) {
    this(element, range, icon, accessibleNameProvider, tooltipProvider, navHandler, alignment);
  }

  /**
   * Creates a line marker info without an icon for the element.
   *
   * @param element         the element for which the line marker is created.
   * @param range     the range (relative to beginning of file) with which the marker is associated
   */
  public LineMarkerInfo(@NotNull T element, @NotNull TextRange range) {
    this(element, range, null, null, null, null, GutterIconRenderer.Alignment.RIGHT /* whatever, won't be used without an icon */);
  }

  /**
   * @deprecated Use {@link #LineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment, Supplier)}
   * or {@link #LineMarkerInfo(PsiElement, TextRange)} instead.
   */
  @Deprecated
  public LineMarkerInfo(@NotNull T element,
                        @NotNull TextRange range,
                        @Nullable Icon icon,
                        @Nullable Function<? super T, @NlsContexts.Tooltip String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler,
                        @NotNull GutterIconRenderer.Alignment alignment) {
    this(element, range, icon, null, tooltipProvider, navHandler, alignment);
  }

  private static @NotNull <T extends PsiElement> SmartPsiElementPointer<T> createElementRef(@NotNull T element, @NotNull TextRange range) {
    PsiFile containingFile = element.getContainingFile();
    Project project = containingFile.getProject();
    TextRange topLevelRange = InjectedLanguageManager.getInstance(project).getTopLevelFile(containingFile).getTextRange();
    if (!topLevelRange.contains(range)) {
      throw new IllegalArgumentException("Range must be inside file offsets "+topLevelRange+" but got: "+range);
    }
    PsiElement firstChild;
    if (!(element instanceof PsiFile) && (firstChild = element.getFirstChild()) != null) {
      String msg = "Performance warning: LineMarker is supposed to be registered for leaf elements only, but got: " +
                   element + " (" + element.getClass() + ") instead. First child: " +
                   firstChild + " (" + firstChild.getClass() + ")" +
                   "\nPlease see LineMarkerProvider#getLineMarkerInfo(PsiElement) javadoc for detailed explanations.";
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(msg);
      }
      else {
        LOG.warn(msg);
      }
    }
    return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element, containingFile);
  }

  LineMarkerInfo(@NotNull T element,
                 @NotNull TextRange range,
                 @Nullable Icon icon,
                 @Nullable Supplier<@Nls @NotNull String> accessibleNameProvider,
                 @Nullable Function<? super T, @NlsContexts.Tooltip String> tooltipProvider,
                 @Nullable GutterIconNavigationHandler<T> navHandler,
                 @NotNull GutterIconRenderer.Alignment alignment) {
    this(createElementRef(element, range), range, icon, accessibleNameProvider, tooltipProvider, navHandler, alignment);
  }

  protected LineMarkerInfo(@NotNull SmartPsiElementPointer<? extends T> elementRef,
                           @NotNull TextRange range,
                           @Nullable Icon icon,
                           @Nullable Supplier<@Nls @NotNull String> accessibleNameProvider,
                           @Nullable Function<? super T, @NlsContexts.Tooltip String> tooltipProvider,
                           @Nullable GutterIconNavigationHandler<T> navHandler,
                           @NotNull GutterIconRenderer.Alignment alignment) {
    myIcon = icon;
    myAccessibleNameProvider = accessibleNameProvider;
    myTooltipProvider = tooltipProvider;
    myIconAlignment = alignment;
    this.elementRef = elementRef;
    myNavigationHandler = navHandler;
    if (navHandler != null) {
      myNavigateAction = new NavigateAction<>(this);
    }
    startOffset = range.getStartOffset();
    endOffset = range.getEndOffset();
    updatePass = 11; //Pass.LINE_MARKERS;
  }

  /**
   * @deprecated use {@link #LineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment, Supplier)}
   * or {@link #LineMarkerInfo(PsiElement, TextRange)} instead
   */
  @Deprecated(forRemoval = true)
  public LineMarkerInfo(@NotNull T element,
                        @NotNull TextRange range,
                        Icon icon,
                        int updatePass,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler,
                        @NotNull GutterIconRenderer.Alignment alignment) {
    this(element, range, icon, null, tooltipProvider, navHandler, alignment);
    PluginException.reportDeprecatedUsage("#LineMarkerInfo(T, TextRange, Icon, int, Function, GutterIconNavigationHandler, Alignment)",
                                          "Please use `LineMarkerInfo(T, TextRange, Icon, Function, GutterIconNavigationHandler, Alignment, Supplier)` instead");
  }

  /**
   * @deprecated use {@link #LineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment, Supplier)}
   * or {@link #LineMarkerInfo(PsiElement, TextRange)} instead
   */
  @Deprecated(forRemoval = true)
  public LineMarkerInfo(@NotNull T element,
                        int startOffset,
                        Icon icon,
                        int updatePass,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler) {
    this(element, new TextRange(startOffset, startOffset), icon, null, tooltipProvider, navHandler, GutterIconRenderer.Alignment.RIGHT);
    PluginException.reportDeprecatedUsage("#LineMarkerInfo(T, int, Icon, int, Function, GutterIconNavigationHandler)",
                                          "Please use `LineMarkerInfo(T, TextRange, Icon, Function, GutterIconNavigationHandler, Alignment, Supplier)` instead");
  }

  public Icon getIcon() {
    return myIcon;
  }

  public GutterIconRenderer createGutterRenderer() {
    return myIcon == null ? null : new LineMarkerGutterIconRenderer<>(this);
  }

  public @NlsContexts.Tooltip String getLineMarkerTooltip() {
    if (myTooltipProvider == null) return null;
    T element = getElement();
    return element == null || !element.isValid() ? null : myTooltipProvider.fun(element);
  }

  public @Nullable T getElement() {
    return elementRef.getElement();
  }

  void setNavigateAction(@NotNull AnAction navigateAction) {
    myNavigateAction = navigateAction;
  }

  public @NotNull MarkupEditorFilter getEditorFilter() {
    return MarkupEditorFilter.EMPTY;
  }

  public final GutterIconNavigationHandler<T> getNavigationHandler() {
    return myNavigationHandler;
  }

  @Nullable Supplier<@NotNull @Nls String> getAccessibleNameProvider() {
    return myAccessibleNameProvider;
  }

  public static class LineMarkerGutterIconRenderer<T extends PsiElement> extends GutterIconRenderer implements DumbAware {
    private final LineMarkerInfo<T> myInfo;

    public LineMarkerGutterIconRenderer(@NotNull LineMarkerInfo<T> info) {
      if (info.myIcon == null) {
        throw new IllegalArgumentException("Must supply not-null icon for the gutter, but got: " + info);
      }
      myInfo = info;
    }

    public @NotNull LineMarkerInfo<T> getLineMarkerInfo() {
      return myInfo;
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public @NotNull Icon getIcon() {
      return myInfo.myIcon;
    }

    @Override
    public @NotNull String getAccessibleName() {
      Supplier<@Nls String> provider = myInfo.myAccessibleNameProvider;
      return provider == null ? super.getAccessibleName() : provider.get();
    }

    @Override
    public AnAction getClickAction() {
      return myInfo.myNavigateAction;
    }

    @Override
    public boolean isNavigateAction() {
      return myInfo.myNavigationHandler != null;
    }

    @Override
    public String getTooltipText() {
      try {
        return myInfo.getLineMarkerTooltip();
      }
      catch (IndexNotReadyException ignored) {
        return null;
      }
    }

    @Override
    public @NotNull Alignment getAlignment() {
      return myInfo.myIconAlignment;
    }

    protected boolean looksTheSameAs(@NotNull LineMarkerGutterIconRenderer<?> renderer) {
      return
        myInfo.elementRef.equals(renderer.myInfo.elementRef)
        && Objects.equals(myInfo.myTooltipProvider, renderer.myInfo.myTooltipProvider)
        && Objects.equals(myInfo.myIcon, renderer.myInfo.myIcon);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LineMarkerGutterIconRenderer && looksTheSameAs((LineMarkerGutterIconRenderer<?>)obj);
    }

    @Override
    public int hashCode() {
      T element = myInfo.getElement();
      return element == null ? 0 : element.hashCode();
    }
  }

  @Override
  public String toString() {
    return "(" + startOffset + "," + endOffset + ") -> " + elementRef + " (icon: " + myIcon + ")";
  }
}
