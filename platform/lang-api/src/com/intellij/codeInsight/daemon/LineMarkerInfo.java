/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.project.IndexNotReadyException;
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
  protected final Icon myIcon;
  private final SmartPsiElementPointer<T> elementRef;
  public final int startOffset;
  public final int endOffset;
  public Color separatorColor;
  public SeparatorPlacement separatorPlacement;
  public RangeHighlighter highlighter;

  public final int updatePass;
  @Nullable private final Function<? super T, String> myTooltipProvider;
  private AnAction myNavigateAction = new NavigateAction<>(this);
  @NotNull private final GutterIconRenderer.Alignment myIconAlignment;
  @Nullable private final GutterIconNavigationHandler<T> myNavigationHandler;

  /**
   * Creates a line marker info for the element.
   * See {@link LineMarkerProvider#getLineMarkerInfo(PsiElement)} javadoc
   * for specific quirks on which elements to use for line markers.
   *
   * @param element         the element for which the line marker is created.
   * @param range     the range (relative to beginning of file) with which the marker is associated
   * @param icon            the icon to show in the gutter for the line marker
   * @param updatePass      the ID of the daemon pass during which the marker should be recalculated
   * @param tooltipProvider the callback to calculate the tooltip for the gutter icon
   * @param navHandler      the handler executed when the gutter icon is clicked
   */
  public LineMarkerInfo(@NotNull T element,
                        @NotNull TextRange range,
                        Icon icon,
                        int updatePass,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler,
                        @NotNull GutterIconRenderer.Alignment alignment) {
    myIcon = icon;
    myTooltipProvider = tooltipProvider;
    myIconAlignment = alignment;
    elementRef = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    myNavigationHandler = navHandler;
    startOffset = range.getStartOffset();
    endOffset = range.getEndOffset();
    this.updatePass = 11; //Pass.LINE_MARKERS;
    PsiElement firstChild;
    if (ApplicationManager.getApplication().isUnitTestMode() && !(element instanceof PsiFile) && (firstChild = element.getFirstChild()) != null) {
      throw new IllegalArgumentException("LineMarker is supposed to be registered for leaf elements only, but got: "+
                element+ " (" +element.getClass()+") instead. First child: "+ firstChild+ " (" +firstChild.getClass()+")"+
                "\nPlease see LineMarkerProvider#getLineMarkerInfo(PsiElement) javadoc for detailed explanations");
    }
  }

  /**
   * @deprecated use {@link LineMarkerInfo#LineMarkerInfo(PsiElement, TextRange, Icon, int, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment)} instead
   */
  public LineMarkerInfo(@NotNull T element,
                        int startOffset,
                        Icon icon,
                        int updatePass,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler,
                        @NotNull GutterIconRenderer.Alignment alignment) {
    this(element, new TextRange(startOffset, startOffset), icon, updatePass, tooltipProvider, navHandler, alignment);
  }

  /**
   * @deprecated use {@link LineMarkerInfo#LineMarkerInfo(PsiElement, TextRange, Icon, int, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment)} instead
   */
  public LineMarkerInfo(@NotNull T element,
                        int startOffset,
                        Icon icon,
                        int updatePass,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler) {
    this(element, startOffset, icon, updatePass, tooltipProvider, navHandler, GutterIconRenderer.Alignment.RIGHT);
  }

  @Nullable
  public GutterIconRenderer createGutterRenderer() {
    if (myIcon == null) return null;
    return new LineMarkerGutterIconRenderer<>(this);
  }

  @Nullable
  public String getLineMarkerTooltip() {
    if (myTooltipProvider == null) return null;
    T element = getElement();
    return element == null || !element.isValid() ? null : myTooltipProvider.fun(element);
  }

  @Nullable
  public T getElement() {
    return elementRef.getElement();
  }

  void setNavigateAction(@NotNull  AnAction navigateAction) {
    myNavigateAction = navigateAction;
  }

  @Nullable
  public GutterIconNavigationHandler<T> getNavigationHandler() {
    return myNavigationHandler;
  }

  public static class LineMarkerGutterIconRenderer<T extends PsiElement> extends GutterIconRenderer {
    private final LineMarkerInfo<T> myInfo;

    public LineMarkerGutterIconRenderer(@NotNull LineMarkerInfo<T> info) {
      myInfo = info;
    }

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

    protected boolean looksTheSameAs(@NotNull LineMarkerGutterIconRenderer renderer) {
      return
        myInfo.getElement() != null &&
        renderer.myInfo.getElement() != null &&
        myInfo.getElement() == renderer.myInfo.getElement() &&
        Comparing.equal(myInfo.myTooltipProvider, renderer.myInfo.myTooltipProvider) &&
        Comparing.equal(myInfo.myIcon, renderer.myInfo.myIcon);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LineMarkerGutterIconRenderer && looksTheSameAs((LineMarkerGutterIconRenderer)obj);
    }

    @Override
    public int hashCode() {
      T element = myInfo.getElement();
      return element == null ? 0 : element.hashCode();
    }
  }

  @Override
  public String toString() {
    return "("+startOffset+","+endOffset+") -> "+elementRef;
  }
}
