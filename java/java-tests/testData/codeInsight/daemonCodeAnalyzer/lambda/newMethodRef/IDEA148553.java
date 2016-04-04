import java.util.function.*;

class Main {
  private static byte[] invoke(byte[] req) {return null;}
  private static String invoke(String req) {return null;}

  private static <T> void send(T req, Supplier<Function<T, T>> methodSelector) {
    System.out.println(req);
    System.out.println(methodSelector);
  }

  public static void main(final String[] args) throws Exception {
    send("", () ->  Main::invoke);
  }
}
