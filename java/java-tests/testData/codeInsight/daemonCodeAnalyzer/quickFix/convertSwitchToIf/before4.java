// "Replace 'switch' with 'if'" "true"
class Precedence {

  void m() {
    int a = 10;
    switch<caret>(a & 1) {
      case 0:
        System.out.println("0");
        break;
      case 1:
        System.out.println("1");
    }
  }
}