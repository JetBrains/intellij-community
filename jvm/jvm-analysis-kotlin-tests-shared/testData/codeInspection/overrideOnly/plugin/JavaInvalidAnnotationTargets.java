package plugin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;

class JavaInvalidAnnotationTargets {
  @OverrideOnly
  public void okayPublicMethod() {}

  @OverrideOnly
  public static void <warning descr="Method 'staticMethod()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">staticMethod</warning>() {}

  @ApiStatus.OverrideOnly
  private void <warning descr="Method 'privateMethodA()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">privateMethodA</warning>() {}

  @OverrideOnly
  private void <warning descr="Method 'privateMethodB()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">privateMethodB</warning>() {}

  @OverrideOnly
  public final void <warning descr="Method 'finalMethod()' is marked with '@ApiStatus.OverrideOnly', but it cannot be overridden">finalMethod</warning>() {}
}

@OverrideOnly
class NonFinalClass {
  void blah() {}
}

@OverrideOnly
class AnotherNonFinalClass {
  @OverrideOnly
  void <warning descr="Annotation '@ApiStatus.OverrideOnly' is redundant">nonFinalMethod</warning>() {}
}

@OverrideOnly
final class <warning descr="Class 'FinalClass' is marked with '@ApiStatus.OverrideOnly', but it cannot be extended, nor its methods overridden">FinalClass</warning> {
  void blah() {}
}

@OverrideOnly
record <warning descr="Record 'SomeRecord' is marked with '@ApiStatus.OverrideOnly', but it cannot be extended, nor its methods overridden">SomeRecord</warning>(String name, int age) {}

@OverrideOnly
enum <warning descr="Enum 'SomeEnum' is marked with '@ApiStatus.OverrideOnly', but it cannot be extended, nor its methods overridden">SomeEnum</warning> { BLACK, WHITE }
