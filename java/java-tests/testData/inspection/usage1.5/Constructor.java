public class Constructor {
  void foo() {
    throw new <error descr="Usage of API documented as @since 1.5+">IllegalArgumentException</error> ("", new RuntimeException());
  }
}