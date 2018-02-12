class Foo {
  public void foo() {
    Integer a = 128;
    Integer b = (int) a;// cast is necessary because new object is created
    Integer c = 128;

    System.out.println(a == b);
    System.out.println(<warning descr="Condition 'a == c' is always 'false'">a == c</warning>);

    System.out.println(<warning descr="Condition '(Long)128L == (Long)128L' is always 'false'">(Long)128L == (Long)128L</warning>);
    System.out.println(<warning descr="Condition '128 == 128' is always 'true'">128 == 128</warning>);

    System.out.println(<warning descr="Condition '(Long)128L != (Long)128L' is always 'true'">(Long)128L != (Long)128L</warning>);
    System.out.println(<warning descr="Condition '128 != 128' is always 'false'">128 != 128</warning>);
  }

}