class Test {
  public static void main(String[] args) {
    if (true) {
      System.out.println();
    }
    else <selection>if (true) {<caret>
      System.out.println();
    }
</selection>    else if (true) {
      System.out.println();
    }
  }
}