class Foo {
  public void foo() {
    Integer a = 128;
    Integer b = (int) a;// cast is necessary because new object is created
    Integer c = 128;

    System.out.println(a == b);
    System.out.println(a == c); // Not known: integer cache could be enlarged and in general VM is free to cache more integral values
    System.out.println(<warning descr="Result of 'a.equals(b)' is always 'true'">a.equals(b)</warning>);
    System.out.println(<warning descr="Result of 'a.equals(c)' is always 'true'">a.equals(c)</warning>);

    System.out.println((Long)128L == (Long)128L);
    System.out.println(<warning descr="Condition '128 == 128' is always 'true'">128 == 128</warning>);

    System.out.println((Long)128L != (Long)128L);
    System.out.println(<warning descr="Condition '128 != 128' is always 'false'">128 != 128</warning>);
  }

}