class Main {

  public static void main(String[] args) {
    Integer a = 1;
    Integer b = 2;
    Long c = 3L;
    System.out.println(true);
    System.out.println(<warning descr="Result of 'c.equals(a+b)' is always 'false'">c.equals(a+b)</warning>);
  }
}