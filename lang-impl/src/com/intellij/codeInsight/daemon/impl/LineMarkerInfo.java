package com.intellij.codeInsight.daemon.impl;

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

public class LineMarkerInfo {

  private Icon myIcon;
  public final WeakReference<PsiElement> elementRef;
  public final int startOffset;
  public TextAttributes attributes;
  public Color separatorColor;
  public SeparatorPlacement separatorPlacement;
  public RangeHighlighter highlighter;
  public final int updatePass;

  private final Function<PsiElement, String> myTooltipProvider;
  private final GutterIconRenderer.Alignment myIconAlignment;
  private final GutterIconNavigationHandler myNavigationHandler;


  public LineMarkerInfo(PsiElement element,
                        int startOffset,
                        Icon icon,
                        int updatePass,
                        Function<PsiElement, String> tooltipProvider,
                        GutterIconNavigationHandler navHandler,
                        GutterIconRenderer.Alignment alignment) {
    myIcon = icon;
    myTooltipProvider = tooltipProvider;
    myIconAlignment = alignment;
    elementRef = new WeakReference<PsiElement>(element);
    myNavigationHandler = navHandler;
    this.startOffset = startOffset;
    this.updatePass = updatePass;
  }

  public LineMarkerInfo(PsiElement element,
                        int startOffset,
                        Icon icon,
                        int updatePass,
                        Function<PsiElement, String> tooltipProvider,
                        GutterIconNavigationHandler navHandler) {
    this(element, startOffset, icon, updatePass, tooltipProvider, navHandler, GutterIconRenderer.Alignment.RIGHT);
  }

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

      public GutterIconRenderer.Alignment getAlignment() {
        return myIconAlignment;
      }
    };
  }

  @Nullable
  private String getLineMarkerTooltip() {
    PsiElement element = elementRef.get();
    if (element == null || !element.isValid()) return null;
    if (myTooltipProvider != null) return myTooltipProvider.fun(element);
    return null;
  }


  private class NavigateAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      if (myNavigationHandler != null) {
        MouseEvent mouseEvent = (MouseEvent)e.getInputEvent();
        PsiElement element = elementRef.get();
        if (element == null || !element.isValid()) return;

        myNavigationHandler.navigate(mouseEvent, element);
      }
    }
  }
}