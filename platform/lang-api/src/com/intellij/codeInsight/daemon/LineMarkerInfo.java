// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LineMarkerInfo<T extends PsiElement> {
  private static final Logger LOG = Logger.getInstance(LineMarkerInfo.class);

  protected final Icon myIcon;
  private final SmartPsiElementPointer<T> elementRef;
  public final int startOffset;
  public final int endOffset;
  public Color separatorColor;
  public SeparatorPlacement separatorPlacement;
  public RangeHighlighter highlighter;

  public final int updatePass;
  private final Function<? super T, String> myTooltipProvider;
  private AnAction myNavigateAction = new NavigateAction<>(this);
  @NotNull
  private final GutterIconRenderer.Alignment myIconAlignment;
  private final GutterIconNavigationHandler<T> myNavigationHandler;

  /**
   * Creates a line marker info for the element.
   * See {@link LineMarkerProvider#getLineMarkerInfo(PsiElement)} javadoc
   * for specific quirks on which elements to use for line markers.
   *
   * @param element         the element for which the line marker is created.
   * @param range     the range (relative to beginning of file) with which the marker is associated
   * @param icon            the icon to show in the gutter for the line marker
   * @param tooltipProvider the callback to calculate the tooltip for the gutter icon
   * @param navHandler      the handler executed when the gutter icon is clicked
   */
  public LineMarkerInfo(@NotNull T element,
                        @NotNull TextRange range,
                        @Nullable Icon icon,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler,
                        @NotNull GutterIconRenderer.Alignment alignment) {
    myIcon = icon;
    myTooltipProvider = tooltipProvider;
    myIconAlignment = alignment;
    PsiFile containingFile = element.getContainingFile();
    Project project = containingFile.getProject();
    TextRange topLevelRange = InjectedLanguageManager.getInstance(project).getTopLevelFile(containingFile).getTextRange();
    if (!topLevelRange.contains(range)) {
      throw new IllegalArgumentException("Range must be inside file offsets "+topLevelRange+" but got: "+range);
    }
    elementRef = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element, containingFile);
    myNavigationHandler = navHandler;
    startOffset = range.getStartOffset();
    endOffset = range.getEndOffset();
    updatePass = 11; //Pass.LINE_MARKERS;
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
  }

  /**
   * @deprecated use {@link LineMarkerInfo#LineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment)} instead
   */
  @Deprecated
  public LineMarkerInfo(@NotNull T element,
                        @NotNull TextRange range,
                        Icon icon,
                        int updatePass,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler,
                        @NotNull GutterIconRenderer.Alignment alignment) {
    this(element, range, icon, tooltipProvider, navHandler, alignment);
  }

  /**
   * @deprecated use {@link LineMarkerInfo#LineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment)} instead
   */
  @Deprecated
  public LineMarkerInfo(@NotNull T element,
                        int startOffset,
                        Icon icon,
                        int updatePass,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler,
                        @NotNull GutterIconRenderer.Alignment alignment) {
    this(element, new TextRange(startOffset, startOffset), icon, tooltipProvider, navHandler, alignment);
  }

  /**
   * @deprecated use {@link LineMarkerInfo#LineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment)} instead
   */
  @Deprecated
  public LineMarkerInfo(@NotNull T element,
                        int startOffset,
                        Icon icon,
                        int updatePass,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler) {
    this(element, new TextRange(startOffset, startOffset), icon, tooltipProvider, navHandler, GutterIconRenderer.Alignment.RIGHT);
  }

  public Icon getIcon() {
    return myIcon;
  }

  public GutterIconRenderer createGutterRenderer() {
    if (myIcon == null) return null;
    return new LineMarkerGutterIconRenderer<>(this);
  }

  public String getLineMarkerTooltip() {
    if (myTooltipProvider == null) return null;
    T element = getElement();
    return element == null || !element.isValid() ? null : myTooltipProvider.fun(element);
  }

  @Nullable
  public T getElement() {
    return elementRef.getElement();
  }

  void setNavigateAction(@NotNull AnAction navigateAction) {
    myNavigateAction = navigateAction;
  }

  @NotNull
  public MarkupEditorFilter getEditorFilter() {
    return MarkupEditorFilter.EMPTY;
  }

  public GutterIconNavigationHandler<T> getNavigationHandler() {
    return myNavigationHandler;
  }

  public static class LineMarkerGutterIconRenderer<T extends PsiElement> extends GutterIconRenderer {
    private final LineMarkerInfo<T> myInfo;

    public LineMarkerGutterIconRenderer(@NotNull LineMarkerInfo<T> info) {
      if (info.myIcon == null) {
        throw new IllegalArgumentException("Must supply not-null icon for the gutter, but got: " + info);
      }
      myInfo = info;
    }

    @NotNull
    public LineMarkerInfo<T> getLineMarkerInfo() {
      return myInfo;
    }

    @Override
    @NotNull
    public Icon getIcon() {
      return myInfo.myIcon;
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

    @NotNull
    @Override
    public Alignment getAlignment() {
      return myInfo.myIconAlignment;
    }

    protected boolean looksTheSameAs(@NotNull LineMarkerGutterIconRenderer<?> renderer) {
      return
        myInfo.getElement() != null &&
        renderer.myInfo.getElement() != null &&
        myInfo.getElement() == renderer.myInfo.getElement() &&
        Comparing.equal(myInfo.myTooltipProvider, renderer.myInfo.myTooltipProvider) &&
        Comparing.equal(myInfo.myIcon, renderer.myInfo.myIcon);
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
    return "("+startOffset+","+endOffset+") -> "+elementRef +" (icon: "+myIcon+")";
  }
}
