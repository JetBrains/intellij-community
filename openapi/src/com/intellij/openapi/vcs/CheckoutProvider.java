package com.intellij.openapi.vcs;

import com.intellij.openapi.components.ApplicationComponent;

/**
 * Implement this interface and register it as ApplicationComponent in order to provide checkout
 */

public interface CheckoutProvider extends ApplicationComponent {
  void doCheckout();
  String getVcsName();
}
