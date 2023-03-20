class X {
  // IDEA-295898
  void test(Object obj) {
    switch (1) {
      default:
        if (obj instanceof String s) {
          System.out.println(s);
        }
    }
  }
}