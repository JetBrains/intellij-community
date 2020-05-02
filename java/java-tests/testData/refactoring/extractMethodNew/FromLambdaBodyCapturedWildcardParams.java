import java.util.stream.Stream;

class Foo {
  public static void main(Stream<? extends String> s) {
    s.filter(ss -> <selection>ss.startsWith("")</selection>);
  }

}