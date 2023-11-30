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
    return new String<caret>Builder("TopProductBean")
      .append(" {productName=").append(productName)
      .append(", mtdRc=").append(mtdRc)
      .append(", pfmRc=").append(pfmRc)
      .append(", pymRc=").append(pymRc) // important
      .append(", ytdRc=").append(ytdRc)
      .append(", pytdRc=").append(pytdRc)
      .append(", yoyRc=").append(yoy)
      .append('}').toString();
  }
}