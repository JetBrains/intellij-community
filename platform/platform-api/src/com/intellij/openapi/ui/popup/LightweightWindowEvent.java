package com.intellij.openapi.ui.popup;

public class LightweightWindowEvent {

  private LightweightWindow myWindow;

  public LightweightWindowEvent(LightweightWindow window) {
    myWindow = window;
  }

  public Balloon asBalloon() {
    return (Balloon)myWindow;
  }

  public JBPopup asPopup() {
    return (JBPopup)myWindow;
  }
}