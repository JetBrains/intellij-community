class Test {

  private int count1;
  private int count2;

  public void test() {
    int oldCount1 = getCount1();
    int oldCount2 = getCount2();
    count1++;
    if (oldCount1 != getCount1() || <warning descr="Condition 'oldCount2 != getCount2()' is always 'false' when reached">oldCount2 != getCount2()</warning>) {
      System.out.println("changed");
    }
  }

  public void test2() {
    int oldCount1 = getCount1();
    int oldCount2 = getCount2();
    count1=count1+1;
    if (oldCount1 != getCount1() || <warning descr="Condition 'oldCount2 != getCount2()' is always 'false' when reached">oldCount2 != getCount2()</warning>) {
      System.out.println("changed");
    }
  }

  private int getCount1() {
    return count1;
  }

  private int getCount2() {
    return count2;
  }

}