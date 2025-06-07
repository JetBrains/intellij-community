class X {
  void test(String str) {
    <caret>  String[] split = str.split("/");
      for (int i = 0, splitLength = split.length; i < splitLength; i++) {
          String s = split[i];
          System.out.println(s);
      }
  }
}