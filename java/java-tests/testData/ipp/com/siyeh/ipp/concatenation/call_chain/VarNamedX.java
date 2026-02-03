class X {

  public static void main(String[] args) {
    int x = 1;
    final String s = new StringBuilder().append(1).<caret>append(2).toString();
    System.out.println("x = " + x);
  }
}