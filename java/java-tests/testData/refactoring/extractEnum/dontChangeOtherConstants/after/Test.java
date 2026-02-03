public class Test {
  public static final int OK = 0;
  public static final int ERROR = 1;

  void foo(int status) {
    switch (status) {
      case OK:
        break;
      case ERROR:
        break;
      case Node.WARNING:
        break;
    }
  }
}

interface Node {
  int WARNING = 2;
}