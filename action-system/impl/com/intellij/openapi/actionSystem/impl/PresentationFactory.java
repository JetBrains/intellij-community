package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;

import java.util.WeakHashMap;

import org.jetbrains.annotations.NotNull;

public class PresentationFactory {
  private WeakHashMap<AnAction,Presentation> myAction2Presentation;

  public PresentationFactory() {
    myAction2Presentation = new WeakHashMap<AnAction, Presentation>();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public final Presentation getPresentation(@NotNull AnAction action){
    Presentation presentation = myAction2Presentation.get(action);
    if (presentation == null){
      presentation = (Presentation)action.getTemplatePresentation().clone();
      myAction2Presentation.put(action, presentation);
    }
    return presentation;
  }

}
