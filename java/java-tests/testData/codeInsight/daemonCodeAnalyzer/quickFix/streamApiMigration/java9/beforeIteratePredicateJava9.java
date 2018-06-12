// "Replace with forEach" "true"

public class Main {
  static class A {
    A next(){return null;}
    int x;
  }

  static boolean isGood(A a) {}

  public long test() {
    for <caret>(A a = new A(); isGood(a); a = a.next()) {
      if(a.x < 3) {
        System.out.println(a);
      }
    }
  }
}