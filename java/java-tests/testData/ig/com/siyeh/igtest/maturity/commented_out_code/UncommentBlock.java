class UnCommentBlock {

  public void x() {
    <warning descr="Commented out code (4 lines)"><caret>/*</warning>System.out.println();
    System.out.println();
    System.out.println();
    System.out.println();*/
  }
}