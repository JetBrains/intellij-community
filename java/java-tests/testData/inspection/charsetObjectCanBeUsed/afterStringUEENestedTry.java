// "Replace with 'StandardCharsets.UTF_16'" "true"
class Test {
  void test(byte[] bytes) {
    String string = null;
      try {
        string = new String(bytes, java.nio.charset.StandardCharsets.UTF_16);
      }
      catch (NullPointerException exception) {
        System.out.println("ex1");
      }
      if(string.startsWith("Foo")) {
      System.out.println("It's a foo!");
    }
  }
}