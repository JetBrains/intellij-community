import java.util.function.Function;

class Test {

  private void foo(final Function<String, String> steps) {
    map(steps::compose);
  }

  <U> void map(Function<Function<String, String>,  U> mapper) {}
}