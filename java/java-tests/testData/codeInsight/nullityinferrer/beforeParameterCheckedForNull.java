import org.jetbrains.annotations.*;

class Test {
  void bar(String str) {
    if (str == null) {
      foo(str);
    }
  }

  String foo(String str) {
    return str;
  }

  String foo1(String str) {
    if (str == null);
    return (str);
  }

  String foo2(String str) {
    if (str == null);
    return ((String)str);
  }

  String fram(String str, boolean b) {
    if (str != null) {
      return b ? str : "not null strimg";
    }
    return "str was null";
  }




}