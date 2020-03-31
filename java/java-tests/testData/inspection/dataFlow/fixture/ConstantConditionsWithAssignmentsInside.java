class Contracts {

  void test(boolean flag) {
    if (flag == (flag = true)) System.out.println();

    int x = 1;
    boolean y = <warning descr="Condition 'x == (x +=1)' is always 'false'">x == (x +=1)</warning>; // returns false
    if (<warning descr="Condition 'y' is always 'false'">y</warning>) System.out.println();

    int k = 1;
    boolean z = <warning descr="Condition '(k +=1) == k' is always 'true'">(k +=1) == k</warning>; // returns true
    if (<warning descr="Condition 'z' is always 'true'">z</warning>) System.out.println();
  }


}