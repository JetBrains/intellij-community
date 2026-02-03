class Foo {
  String foo() {
    <selection>String var = "";
    if (var == null) {
      return null;
    }</selection>
    System.out.println(var);
  }
}