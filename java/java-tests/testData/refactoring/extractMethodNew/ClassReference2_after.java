import org.jetbrains.annotations.NotNull;

class C {
  Object foo(boolean b) {
    if (b) {
        return newMethod();
    } else {
        return newMethod();
    }
  }

    @NotNull
    private Object newMethod() {
        return A.getInstance();
    }
}
class A {
  static A getInstance() {
    return new A();
  }
}
class B extends A {
}