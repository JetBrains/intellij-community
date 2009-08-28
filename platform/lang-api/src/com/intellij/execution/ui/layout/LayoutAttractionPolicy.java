package com.intellij.execution.ui.layout;

import com.intellij.ui.content.Content;
import com.intellij.execution.ui.RunnerLayoutUi;

public abstract class LayoutAttractionPolicy {

  public abstract void attract(Content content, RunnerLayoutUi ui);

  public abstract void clearAttraction(Content content, RunnerLayoutUi ui);

  public static class Bounce extends LayoutAttractionPolicy {
    public void attract(final Content content, final RunnerLayoutUi ui) {
      ui.setBouncing(content, true);
    }

    public void clearAttraction(final Content content, final RunnerLayoutUi ui) {
      ui.setBouncing(content, false);
    }
  }

  public static class FocusOnce extends LayoutAttractionPolicy {

    private boolean myWasAttracted;

    public void attract(final Content content, final RunnerLayoutUi ui) {
      if (!myWasAttracted) {
        myWasAttracted = true;
        ui.selectAndFocus(content, true);
      } else {
        ui.setBouncing(content, true);
      }
    }

    public void clearAttraction(final Content content, final RunnerLayoutUi ui) {
      ui.setBouncing(content, false);
    }
  }

  public static class FocusAlways extends LayoutAttractionPolicy {
    public void attract(final Content content, final RunnerLayoutUi ui) {
      ui.selectAndFocus(content, true);
    }

    public void clearAttraction(final Content content, final RunnerLayoutUi ui) {
    }
  }

}