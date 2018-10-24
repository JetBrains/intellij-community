// "Replace 'switch' with 'if'" "true"
class X {
  int m(int i) {
    if (i > 0) {
        int i1 = ++i;
        if (i1 == 1) {
            System.out.println(1);

            System.out.println(2);
        } else if (i1 == 2) {
            System.out.println(2);
        }
    }
  }
}