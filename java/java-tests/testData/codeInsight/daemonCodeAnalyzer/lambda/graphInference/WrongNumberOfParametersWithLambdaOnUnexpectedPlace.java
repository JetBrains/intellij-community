import java.util.function.UnaryOperator;
import java.util.stream.Stream;

class Main {
  static class A {
    A next(){return null;}
    int x;
  }

  static boolean isGood(A a) {
    return true;
  }

  {
      Stream.iterate(new A(), (UnaryOperator<A>) <error descr="Bad return type in method reference: cannot convert boolean to Main.A">Main::isGood</error>, a -> a.<error descr="Cannot resolve method 'next()'">next</error>()).filter(a -> a.x < 3).forEach(System.out::println);
  }
}