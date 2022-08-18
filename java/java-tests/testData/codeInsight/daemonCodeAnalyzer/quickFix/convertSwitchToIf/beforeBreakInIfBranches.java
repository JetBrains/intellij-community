// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test(int x) {
    if (x > 0) {
      swi<caret>tch (x) {
        case 1:
          if (Math.random() > 0.5) {
            System.out.println(1);
            break;
          } else {
            break;
          }
        case 2:
          System.out.println(2);
          break;
      }
    }
    System.out.println("Exit");
  }
}