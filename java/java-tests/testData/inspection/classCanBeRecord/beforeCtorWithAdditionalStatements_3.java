// "Convert to record class" "true-preview"

class Main<caret> {
  private final int a;
  private final int b;

  Main(int a, int b) {
    this.a = a;
    this.b = b;
  }

  Main(int a) {
    this.a = a;
    this.b = 0;
    {
      System.out.println("Some random code block");
    }
    for (int i = 0; i < 42; i++) {
      System.out.println("i is " + i);
    }
  }

  int a() {
    return a;
  }

  int b() {
    return b;
  }
}
