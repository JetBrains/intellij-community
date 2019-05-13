import java.util.function.Consumer;

public class Subst {
  void test1() {
    test2(Subst::bar);
  }

  private static void bar(Object o) {

  }

  void test2(final Consumer<Object> consu<caret>mer) {}
}
