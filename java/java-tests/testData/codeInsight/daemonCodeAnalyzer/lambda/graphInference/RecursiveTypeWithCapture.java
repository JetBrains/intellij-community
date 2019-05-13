
import java.util.List;

class Test {
  <B> void foo(final Enum<? extends List<B>> f) {}

  void bar(final Enum<? extends List<String>> e) {
    foo(e);
  }
}