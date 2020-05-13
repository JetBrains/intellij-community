import org.jetbrains.annotations.NotNull;

class C {
  Object foo(boolean b) {
    if (b) {
        return newMethod();
    } else {
      return B.getInstance();
    }
  }

    @NotNull
    private A newMethod() {
        return A.getInstance();
    }
}
class A {
  static A getInstance() {
    return new A();
  }
}
class B extends A {
  static B getInstance() {
    return new B();
  }
}