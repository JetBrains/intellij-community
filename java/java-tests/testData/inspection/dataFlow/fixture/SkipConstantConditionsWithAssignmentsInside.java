class Contracts {

  void test(boolean flag) {
    if (flag == (flag = true)) System.out.println();

    int x = 1;
    boolean y = x == (x +=1); // returns false
    if (y) System.out.println();

    int k = 1;
    boolean z = (k +=1) == k; // returns true
    if (z) System.out.println();
  }


}