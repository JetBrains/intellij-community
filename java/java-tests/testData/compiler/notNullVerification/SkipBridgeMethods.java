import org.jetbrains.annotations.NotNull;

public class SkipBridgeMethods {
  public static void main() {
    A a = new B();
    a.getObject(null);
  }
}

class A {
  @NotNull
  public Object getObject(Object arg) {
    return new Object();
  }
}

class B extends A {
  @NotNull
  public String getObject(@NotNull Object arg) {
    return arg.toString();
  }
}