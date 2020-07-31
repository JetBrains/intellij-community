// "Replace 'switch' with 'if'" "true"
class X {
  int m(int i) {
    if (i > 0) {
        int j = ++i;
        if (j == 1) {
            System.out.println(1);

            System.out.println(2);
        } else if (j == 2) {
            System.out.println(2);
        }
    }
  }
}