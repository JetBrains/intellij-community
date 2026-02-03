package org.jetbrains.providers;

import org.jetbraons.api.MyProviderInterface;

public class WithProvider implements MyProviderInterface {
  <caret>
  public static WithProvider provider() {
    return new WithProvider();
  }
}
