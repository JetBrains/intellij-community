class X {

  public static void main(String[] args) {
    System.out.println("took " + (System//simple end comment
                                    .currentTi<caret>meMillis() - 1) + "ms");
  }

}