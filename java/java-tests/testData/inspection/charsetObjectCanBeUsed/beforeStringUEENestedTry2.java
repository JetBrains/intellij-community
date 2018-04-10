// "Replace with 'StandardCharsets.UTF_16'" "true"
class Test {
  void test(byte[] bytes) {
    String string = null;
    try {
      try {
        string = new String(bytes, "UTF<caret>-16");
      }
      catch (Exception exception) {
        System.out.println("ex1");
      }
    }
    // this catch is already unnecessary, so don't remove it
    catch (java.io.UnsupportedEncodingException exception) {
      exception.printStackTrace();
    }
    if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}