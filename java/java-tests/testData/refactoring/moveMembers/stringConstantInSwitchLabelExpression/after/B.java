public class B {

    public static final String FOO = "FOO";
}

class U {
  public void example(String foo) {
    switch (foo) {
      case B.FOO:
        break;
    }
  }
}