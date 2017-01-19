import java.util.stream.Stream;

class Foo {
  public static void main(Stream<? extends String> s) {
    s.filter(ss -> newMethod(ss));
  }

    private static boolean newMethod(String ss) {
        return ss.startsWith("");
    }

}