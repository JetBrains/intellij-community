class Foo {
  public void foo() {
    Integer a = 128;
    Integer b = (int) a;// cast is necessary because new object is created
    Integer c = 128;

    System.out.println(a == b);
    System.out.println(<warning descr="Condition 'a == c' is always 'false'">a == c</warning>);
  }

}