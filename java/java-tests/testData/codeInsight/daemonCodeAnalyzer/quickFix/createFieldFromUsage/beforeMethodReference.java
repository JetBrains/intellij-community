// "Create field 'foo' in 'Target'" "false"

public class MethodReference {
  void test() {
    Supplier<Target> supplier = Target::new;
    Supplier<Target> s = Target::<caret>foo;
  }
}

class Target {

  private Target() {}

  private static Target foo() {
    return null;
  }
}