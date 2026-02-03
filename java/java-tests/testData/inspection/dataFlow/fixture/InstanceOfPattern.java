import java.util.function.*;
import org.jetbrains.annotations.Nullable;

public class InstanceOfPattern {
  void test2(@Nullable Foo foo) {
    if (Math.random() > 0.5 && foo.<warning descr="Method invocation 'bar' may produce 'NullPointerException'">bar</warning>() instanceof String s) {
      System.out.println(s.length());
    }
    if (Math.random() > 0.5 && foo.<warning descr="Method invocation 'getBar' may produce 'NullPointerException'">getBar</warning>() instanceof String s) {
      System.out.println(s.length());
    }
  }

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
    if (s instanceof <error descr="Pattern type 'String' is the same as expression type">String</error> s1) {
      System.out.println(s1);
    }
  }

  void testNullCheckUnusedPatternVariable(String s) {
    if (s instanceof <error descr="Pattern type 'String' is the same as expression type">String</error> s1 && s1.length()==2) {
      System.out.println("foo");
    }
  }

  interface Foo {
    @Nullable Object getBar();
    
    @Nullable Object bar();
  }
}