package library;

import org.jetbrains.annotations.ApiStatus.OverrideOnly;

public abstract class JavaMembersOverrideOnly {
  @OverrideOnly
  public abstract void implementOnlyMethod();

  @OverrideOnly
  public static void staticMethod() { }

  @OverrideOnly
  public final void finalMethod() { }
}
