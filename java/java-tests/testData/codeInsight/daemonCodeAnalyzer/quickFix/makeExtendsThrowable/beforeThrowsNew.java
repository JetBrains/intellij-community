// "Make 'C' extend 'java.lang.Throwable'" "true"

class C {}
class Main {
  public static void main(String[] args) {
    throw new <caret>C();
  }
}
