class Foo {
  String parentName;
  void m() {
      String parentName1 = parentName;
      System.out.println(parentName);
  }
}