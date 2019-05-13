interface A {
  void foo();
}

class B implements A {
    @Override
    public void foo() {
        foo(this);
    }

    public static void foo(B anObject) {}
}