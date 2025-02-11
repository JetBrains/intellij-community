// IDEA-367309
class BugReproducer {
  public static final SomeClass STATIC_FIELD = new SomeClass();

  public static final SomeInterface INSTANCE = new SomeInterface() {
    private final SomeClass myField = STATIC_FIELD;

    public void doSomething() {
      myField.method();
    }
  };

  public static void main(String[] args) {
    INSTANCE.doSomething();
  }
}

class SomeClass {
  void method() {
    System.out.println("Method called");
  }
}

interface SomeInterface {
  void doSomething();
}