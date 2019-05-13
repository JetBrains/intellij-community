// "Swap 'if' statements" "true"
class A {

  void m() {

    if (a) {
      System.out.println(1);
    } else if (b) {
      System.out.println(2);
    } el<caret>se if (c) {
      System.out.println(3);
    } else if (d) {
      System.out.println(4);
    } else {
      System.out.println(5);
    }

  }

}