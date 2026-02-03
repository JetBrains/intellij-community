import java.util.Map;

class Main {

  public static <T extends Map<? extends String, ? extends String>> T test() {
    return null;
  }

  public static void main(String[] args) {
    Map<String, String> m = Main.test();
  }
}

