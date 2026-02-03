class Foo {
  String foo() {
    <selection>String var = null;
    if (var == null) {
      return "";
    }</selection>
    System.out.println(var);
  }
}