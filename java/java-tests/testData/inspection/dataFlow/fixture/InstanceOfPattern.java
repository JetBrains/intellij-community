import java.util.function.*;
import org.jetbrains.annotations.Nullable;

public class InstanceOfPattern {
  void test(Foo foo) {
    if (foo.bar() instanceof String s) {
      System.out.println(s.length());
    }
  }

  void test(Object obj) {
    if (obj instanceof Number n) {
      if (<warning descr="Condition 'n == obj' is always 'true'">n == obj</warning>) {}
    }
  }

  void testNullCheck(String s) {
    if (s instanceof String s1) {
      System.out.println(s1);
    }
  }

  void testNullCheckUnusedPatternVariable(String s) {
    if (s instanceof String s1) {
      System.out.println("foo");
    }
  }

  interface Foo {
    @Nullable Object bar();
  }
}