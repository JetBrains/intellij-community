import java.io.BufferedReader;

public class A {
  void test() {
    Object obj = new BufferedReader(new UnknownClass(<caret>));
  }
}