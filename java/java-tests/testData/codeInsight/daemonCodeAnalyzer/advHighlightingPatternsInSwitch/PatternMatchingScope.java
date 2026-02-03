
public class Main {
  int expr(Object o) {
    return switch(o) {
      case String s -> 1;
      case String s1 -> <error descr="Cannot resolve symbol 's'">s</error>.length();
      case Integer i -> i;
      default -> 0;
    };
  }

  static void statement(Object o) {
    switch(o) {
      case String s:
      case String s1:
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>.length());
      case Integer i:
        System.out.println(i);
      default:
        System.out.println(1);
    };
  }

  static void nested(Object o, String title) {
    String header = "Hello";
    switch (o) {
      case String s:
        switch (o) {
          case String <error descr="Variable 's' is already defined in the scope">s</error>:
          case String s1:
            System.out.println(title + header + ":" + s);
        }
    }
  }
}