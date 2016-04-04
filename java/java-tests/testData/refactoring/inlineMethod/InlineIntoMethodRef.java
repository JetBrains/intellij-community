import java.util.function.Supplier;

class Test {
  {
    Supplier<String> sup = this::g<caret>et;
  }

  private String get() {
    return null;
  }
}