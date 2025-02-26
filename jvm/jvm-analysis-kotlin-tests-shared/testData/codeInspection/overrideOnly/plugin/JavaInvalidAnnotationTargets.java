package plugin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;

class JavaInvalidAnnotationTargets {
  @OverrideOnly
  public final void <warning descr="Method 'staticMethod()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">staticMethod</warning>() {}

  @ApiStatus.OverrideOnly
  public final void <warning descr="Method 'privateMethodA()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">privateMethodA</warning>() {}

  @OverrideOnly
  public final void <warning descr="Method 'privateMethodB()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">privateMethodB</warning>() {}

  @OverrideOnly
  public final void <warning descr="Method 'finalMethod()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">finalMethod</warning>() {}
}
