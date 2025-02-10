package library;

import org.jetbrains.annotations.ApiStatus.OverrideOnly;

@OverrideOnly
public interface JavaInterfaceOverrideOnly {

  void implementOnlyMethod();

  static void staticMethod() {}
}
