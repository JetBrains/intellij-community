class Test {
  void test(Object o, Integer integer) {
    switch (o) {
      case String s when <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'boolean'">integer</error> -> System.out.println();
      default -> {}
    }

    switch (o) {
      case String s when isBool() -> System.out.println();
      default -> {}
    }

    switch (o) {
      case Integer i when <error descr="Incompatible types. Found: 'int', required: 'boolean'">isInt()</error>:
        break;
      default:
        break;
    }

    switch (o) {
      case Integer i when <error descr="Incompatible types. Found: 'null', required: 'boolean'">null</error>:
        break;
      default:
        break;
    }
  }

  private native boolean isBool();

  private native int isInt();
}