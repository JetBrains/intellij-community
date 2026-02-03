// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test(int x) {
    if (x > 0) {
        if (x == 1) {
            if (Math.random() > 0.5) {
                System.out.println(1);
            } else {
            }
        } else if (x == 2) {
            System.out.println(2);
        }
    }
    System.out.println("Exit");
  }
}