import java.lang.String;

abstract class Test {
  abstract <T> T get();

  void foo() {
    String.valueOf(get());
  }
}