class Main {
  private static final <X extends Integer, Y extends X> void add(X x, Y y) {
    System.out.println(x + y);
  }

  private static <X extends String, Y extends X> void concat(X x, Y y) {
    System.out.println(<error descr="Operator '+' cannot be applied to 'X', 'Y'">x+y</error>);
  }

  public static void main(String[] args) {
    add(10, 20);
    concat("Hello", "World");
  }
}
