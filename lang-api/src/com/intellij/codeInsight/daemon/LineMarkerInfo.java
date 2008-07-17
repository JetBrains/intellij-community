package com.intellij.codeInsight.daemon;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;

public class LineMarkerInfo<T extends PsiElement> {
  private final Icon myIcon;
  private final WeakReference<T> elementRef;
  public final int startOffset;
  public int endOffset;
  public Color separatorColor;
  public SeparatorPlacement separatorPlacement;
  public RangeHighlighter highlighter;

  public final int updatePass;
  @Nullable private final Function<? super T, String> myTooltipProvider;
  private final GutterIconRenderer.Alignment myIconAlignment;
  @Nullable private final GutterIconNavigationHandler<T> myNavigationHandler;
  public TextAttributes textAttributes;


  public LineMarkerInfo(T element,
                        int startOffset,
                        Icon icon,
                        int updatePass,
                        @Nullable Function<? super T, String> tooltipProvider,
                        @Nullable GutterIconNavigationHandler<T> navHandler,
                        GutterIconRenderer.Alignment alignment) {
    myIcon = icon;
    myTooltipProvider = tooltipProvider;
    myIconAlignment = alignment;
    elementRef = new WeakReference<T>(element);
    myNavigationHandler = navHandler;
    this.startOffset = startOffset;
    this.updatePass = updatePass;
    endOffset = startOffset;
  }

  public LineMarkerInfo(T element,
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
    return new GutterIconRenderer() {
      @NotNull
      public Icon getIcon() {
        return myIcon;
      }

      public AnAction getClickAction() {
        return new NavigateAction();
      }

      public boolean isNavigateAction() {
        return myNavigationHandler != null;
      }

      public String getTooltipText() {
        return getLineMarkerTooltip();
      }

      public Alignment getAlignment() {
        return myIconAlignment;
      }
    };
  }

  @Nullable
  public String getLineMarkerTooltip() {
    T element = getElement();
    if (element == null || !element.isValid()) return null;
    if (myTooltipProvider != null) return myTooltipProvider.fun(element);
    return null;
  }

  public T getElement() {
    return elementRef.get();
  }


  private class NavigateAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      if (myNavigationHandler != null) {
        MouseEvent mouseEvent = (MouseEvent)e.getInputEvent();
        T element = getElement();
        if (element == null || !element.isValid()) return;

        myNavigationHandler.navigate(mouseEvent, element);
      }
    }
  }

  @Nullable
  public GutterIconNavigationHandler<T> getNavigationHandler() {
    return myNavigationHandler;
  }
}