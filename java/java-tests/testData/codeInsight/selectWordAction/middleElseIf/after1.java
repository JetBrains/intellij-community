class Test {
  public static void main(String[] args) {
    if (true) {
      System.out.println();
    }
    else if (true) <selection>{<caret>
      System.out.println();
    }</selection>
    else if (true) {
      System.out.println();
    }
  }
}