// "Replace 'switch' with 'if'" "true"
class Precedence {

  void m() {
    int a = 10;
      if ((a & 1) == 0) {
          System.out.println("0");
      } else if ((a & 1) == 1) {
          System.out.println("1");
      }
  }
}