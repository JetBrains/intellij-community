package plugin;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.NonExtendable;

class JavaInvalidAnnotationTargets {
  @NonExtendable
  public final void <warning descr="Method 'staticMethod()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">staticMethod</warning>() {}

  @ApiStatus.NonExtendable
  private void <warning descr="Method 'privateMethodA()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">privateMethodA</warning>() {}

  @NonExtendable
  private void <warning descr="Method 'privateMethodB()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">privateMethodB</warning>() {}

  @NonExtendable
  final void <warning descr="Method 'finalMethod()' is marked with '@ApiStatus.NonExtendable', but it cannot be overridden">finalMethod</warning>() {}
}

@NonExtendable
class NonFinalClass {
  void blah() {}
}

@ApiStatus.NonExtendable
class MyClass {

  @ApiStatus.NonExtendable
  public void <warning descr="Annotation '@ApiStatus.NonExtendable' is redundant">myFun</warning>() {}

  public void myAnotherFun() {}
}

@NonExtendable
final class <warning descr="Class 'FinalClass' is marked with '@ApiStatus.NonExtendable', but it cannot be extended">FinalClass</warning> {
  void blah() {}
}

@NonExtendable
record <warning descr="Record 'SomeRecord' is marked with '@ApiStatus.NonExtendable', but it cannot be extended">SomeRecord</warning>(String name, int age) {}

@NonExtendable
enum <warning descr="Enum 'SomeEnum' is marked with '@ApiStatus.NonExtendable', but it cannot be extended">SomeEnum</warning> { BLACK, WHITE }
