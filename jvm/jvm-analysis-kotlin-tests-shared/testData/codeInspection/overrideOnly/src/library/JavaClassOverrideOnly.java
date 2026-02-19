package library;

import org.jetbrains.annotations.ApiStatus.OverrideOnly;

@OverrideOnly
public abstract class JavaClassOverrideOnly {

  public abstract void overrideOnlyMethod();

  public static void staticMethod() { }

  public final void finalMethod() { }
}
