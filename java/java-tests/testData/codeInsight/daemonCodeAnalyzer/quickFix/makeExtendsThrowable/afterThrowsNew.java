// "Make 'C' extend 'java.lang.Throwable'" "true-preview"

class C extends Throwable <caret>{}
class Main {
  public static void main(String[] args) {
    throw new C();
  }
}
