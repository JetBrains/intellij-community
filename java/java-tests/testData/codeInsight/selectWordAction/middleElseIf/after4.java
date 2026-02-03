class Test {
  public static void main(String[] args) {
    if (true) {
      System.out.println();
    }
<selection>    else if (true) {<caret>
      System.out.println();
    }
    else if (true) {
      System.out.println();
    }
</selection>  }
}