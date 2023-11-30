
public class Main {
  public A field;
  public static void main(String[] args) {
    init();
    field.baz();
  }

  private static void init()
  {
    if (condition()) {
      field = new A;
    }
    else {
      field = new B;
    }
  }
}

