class Foo {
  public int foo(String str) {
    return switch (str) {
        case MyConstants<caret>
    };
  }
}

class MyConstants {
  public static final String STRING_ONE = "1";
}