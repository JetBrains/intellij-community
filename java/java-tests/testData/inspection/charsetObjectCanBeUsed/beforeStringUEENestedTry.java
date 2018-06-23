// "Replace with 'StandardCharsets.UTF_16'" "true"
class Test {
  void test(byte[] bytes) {
    String string = null;
    try {
      try {
        string = new String(bytes, "utf<caret>16");
      }
      catch (NullPointerException exception) {
        System.out.println("ex1");
      }
    }
    catch (java.io.UnsupportedEncodingException exception) {
      exception.printStackTrace();
    }
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}