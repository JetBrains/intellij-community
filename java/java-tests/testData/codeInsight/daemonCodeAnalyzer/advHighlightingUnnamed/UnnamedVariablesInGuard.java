import java.util.function.*;

interface Something {

}

enum E implements Something {A, B,}

record P(String s) implements Something {
}

class Test {
  public static void main(String[] args) {

  }
  public int main1(Something something) {
    return switch (something) {
      case P(_) when something instanceof Object o: yield o.hashCode();
      case E _ when something instanceof Object o: yield o.hashCode();
      default: {
        yield 2;
      }
    };
  }

  public int main2(Something something) {
    return switch (something) {
      case P(_) when something instanceof Object o: yield o.hashCode();
      case E _ when something instanceof Object o1: yield <error descr="Cannot resolve symbol 'o'">o</error>.hashCode();
      default: {
        yield 2;
      }
    };
  }
}