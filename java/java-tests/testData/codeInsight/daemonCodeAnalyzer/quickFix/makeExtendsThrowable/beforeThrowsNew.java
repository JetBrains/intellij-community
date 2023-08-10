// "Make 'C' extend 'java.lang.Throwable'" "true-preview"

class C {}
class Main {
  public static void main(String[] args) {
    throw new <caret>C();
  }
}
