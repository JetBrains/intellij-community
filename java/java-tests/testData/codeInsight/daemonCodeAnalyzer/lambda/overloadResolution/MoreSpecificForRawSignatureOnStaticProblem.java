import java.util.HashSet;
import java.util.Set;

class TestCaller1<P1> {
  {
    TestCaller1.foo(null);
  }
  public static void foo(HashSet<Integer> fn) { }
  public <P> void foo(Set<String> fn) { }
}

class TestCaller2 {
  {
    TestCaller2.foo<error descr="Ambiguous method call: both 'TestCaller2.foo(HashSet<Integer>)' and 'TestCaller2.foo(Set<String>)' match">(null)</error>;
  }
  public static void foo(HashSet<Integer> fn) { }
  public <P> void foo(Set<String> fn) { }
}

class TestCaller3 {
  {
    TestCaller2.foo<error descr="Ambiguous method call: both 'TestCaller2.foo(HashSet<Integer>)' and 'TestCaller2.foo(Set<String>)' match">(null)</error>;
  }
  public static void foo(HashSet<Integer> fn) { }
  public static void foo(Set<String> fn) { }
}