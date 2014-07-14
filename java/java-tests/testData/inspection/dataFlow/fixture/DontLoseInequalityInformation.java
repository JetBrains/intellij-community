class Some {
  public static void main(int i) {
    if (i != 0) {
      if (i == 1 || i == 2 || i == 3) {
        System.out.println("hello");
      }
      if (<warning descr="Condition 'i == 0' is always 'false'">i == 0</warning>) {
        System.out.println("wat?");
      }
    }
  }

}
