import java.util.*;
import org.jetbrains.annotations.*;

class MergedInitializerAndConstructor {
  static class Test1 {
    private Collection<Object> collection2 = null;

    public Test1() {
      collection2.<warning descr="Method invocation 'add' may produce 'java.lang.NullPointerException'">add</warning>("");
    }
  }

  static class Test2 {
    private Collection<Object> collection2 = null;

    public Test2() {
      collection2.add("");
    }

    {
      collection2.<warning descr="Method invocation 'add' may produce 'java.lang.NullPointerException'">add</warning>(""); //<- warning here
    }
  }

  static class Test3 {
    private Collection<Object> collection2 = null;

    public Test3(String s) {
      super();
      collection2.<warning descr="Method invocation 'add' may produce 'java.lang.NullPointerException'">add</warning>(s);
    }
  }

  static class Test4 {
    private Collection<Object> collection2 = null;

    @Contract(pure = true)
    public Test4() {
      collection2 = new ArrayList<>();
    }

    public Test4(String s) {
      this();
      collection2.add(s);
    }
  }

  static class Super {
    Super() {
      init();
    }

    void init() {}
  }

  static class Test5 extends Super {
    private Collection<Object> collection2 = null;

    public Test5() {
      // implicit super initializes collection2
      collection2.add("foo");
    }

    public Test5(String s) {
      super();
      collection2.add(s);
    }

    void init() {
      collection2 = new ArrayList<>();
    }
  }

  static class DoNotWarnOnDefaultInit {
    private int x;
    private @Nullable Object val;

    DoNotWarnOnDefaultInit() {
      // Do not issue "Variable is already assigned" here
      x = 0;
      val = null;
    }
  }
}
