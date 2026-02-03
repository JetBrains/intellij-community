// "Replace Stream API chain with loop" "false"

import java.util.stream.Stream;

public class Test {
  interface A {void a();}
  interface B {void b();}

  public void test(Object obj) {
    Stream.of((A & B) obj).fo<caret>rEach(B::b);
  }
}