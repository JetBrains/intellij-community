class Test {

  private static void f(boolean a, boolean b) {
    if (a) {
      <selection>try {
        System.out.println();
        return;
      }
      catch (Exception e) {
        return;
      }</selection>
    } else {
      System.out.println("");
    }
  }

}