public class Test {
  void withDefaultWithoutBreak(MyEnum e) {
    String foo = null;
    switch (e) {
      case FOO:
        foo = "foo";
      case BAR:
        foo = "bar";
      default:
        foo = "default";
    }
    int l = foo.length();
  }

  void withDefaultWithBreak(MyEnum e) {
    String foo = null;
    switch (e) {
      case FOO:
        foo = "foo";
        break;
      case BAR:
        foo = "bar";
        break;
      default:
        foo = "default";
    }
    int l = foo.length();
  }

  void withDefaultWithoutBar(MyEnum e) {
    String foo = null;
    switch (e) {
      case FOO:
        foo = "foo";
        break;
      default:
        foo = "default";
    }
    int l = foo.length();
  }

  void withoutDefaultWithBreak(MyEnum e) {
    String foo = null;
    switch (e) {
      case FOO:
        foo = "foo";
        break;
      case BAR:
        foo = "bar";
        break;
    }
    int l = foo.length();
  }

  void withoutDefaultWithoutBreak(MyEnum e) {
    String foo = null;
    switch (e) {
      case FOO:
        foo = "foo";
      case BAR:
        foo = "bar";
    }
    int l = foo.length();
  }

  void withoutDefaultWithoutBar(MyEnum e) {
    String foo = null;
    switch (e) {
      case FOO:
        foo = "foo";
    }
    int l = foo.length();
  }
}

enum MyEnum {
  FOO, BAR;
}