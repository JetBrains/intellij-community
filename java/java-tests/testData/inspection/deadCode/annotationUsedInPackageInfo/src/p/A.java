package p;

public @interface A {
  Class<?> val();
  String constant();
}

class B {}
class C {
  public static final String FOO = "";
}