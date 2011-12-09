package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;

/**
* Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: 12/9/11
* Time: 5:29 PM
* To change this template use File | Settings | File Templates.
*/
public class CustomEditInspectionToolsSettingsAction extends EditInspectionToolsSettingsAction {
  private final Computable<String> myText;

  public CustomEditInspectionToolsSettingsAction(HighlightDisplayKey displayKey, Computable<String> text) {
    super(displayKey);
    myText = text;
  }

  @NotNull
  @Override
  public String getText() {
    return myText.compute();
  }
}
