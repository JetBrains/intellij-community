class Complex1 {

  private String productName;
  private int mtdRc;
  private int pfmRc;
  private int pymRc;
  private int ytdRc;
  private int pytdRc;
  private int yoy;

  @Override
  public String toString() {
    return "TopProductBean" +
            " {productName=" + productName +
            ", mtdRc=" + mtdRc +
            ", pfmRc=" + pfmRc +
            ", pymRc=" + pymRc + // important
            ", ytdRc=" + ytdRc +
            ", pytdRc=" + pytdRc +
            ", yoyRc=" + yoy +
            '}';
  }
}