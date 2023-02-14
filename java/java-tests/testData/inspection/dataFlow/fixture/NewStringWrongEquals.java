import java.nio.charset.*;

class Main {
  private static void test() {
    Key key = new Key(getBytes());
    String s = key.value();
    String s1 = new String(getBytes(), StandardCharsets.US_ASCII);

    System.out.println("info".equals(s1));
    System.out.println("info".equals(s));
  }

  public static void main(String[] args) {
    test();
  }

  private static byte[] getBytes() {
    return "info".getBytes(StandardCharsets.US_ASCII);
  }

  private record Key(byte[] bytes) {
    public String value () {
      return new String(bytes, StandardCharsets.US_ASCII);
    }
  }
}