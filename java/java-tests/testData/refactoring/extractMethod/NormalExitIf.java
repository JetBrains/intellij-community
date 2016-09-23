class Test {

  private static void f(boolean a, boolean b) {
    if (a) {
      <selection>if (b) {
        System.out.println("");
        return;
      } else {
        System.out.println("");
        return;
      }</selection>
    } else {
      System.out.println("");
    }
  }

}