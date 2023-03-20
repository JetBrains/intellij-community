class Foo {
  public int foo(String str) {
    return switch (str) {
      case MyCons<caret>
    };
  }
}

class MyConstants {
  public static final String STRING_ONE = "1";
}