package org.jetbrains.annotations;

final class ApiStatus {
  public @interface ScheduledForRemoval {}
}

class X {
  /**
   * @deprecated use {@link #bar()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void foo() {}
  
  public static void bar() {}
}
class Use {
  void test() {
    X.<error descr="'foo()' is scheduled for removal in a future version"><caret>foo</error>();
  }
}

