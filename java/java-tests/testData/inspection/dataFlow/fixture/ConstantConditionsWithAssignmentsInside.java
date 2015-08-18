class Contracts {

  void test(boolean flag) {
    if (flag == (flag = true)) System.out.println();

    int x = 1;
    boolean y = x == (x +=1); // returns false
    if (y) System.out.println();

    int k = 1;
    boolean z = <warning descr="Condition '(k +=1) == k' is always 'true'">(k +=1) == k</warning>; // returns true
    if (<warning descr="Condition 'z' is always 'true'">z</warning>) System.out.println();
  }


}