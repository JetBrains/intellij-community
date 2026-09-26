// "Surround with 'if (obj instanceof String)'" "true-preview"
class X {
  void test(Object obj) {
    if (obj instanceof Integer) System.out.println();
      if (obj instanceof String) {
          String string = (String)obj;
      }
  }
}