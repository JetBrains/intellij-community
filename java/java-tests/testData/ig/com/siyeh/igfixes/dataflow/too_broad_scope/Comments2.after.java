class Comments2 {

  public int methodThree(final int data) {
      if (data == 0) {
      throw new IllegalArgumentException("DATA_CANT_BE_ZERO");
    }
      // !!!
      int alpha = (data) + 5;
      return process(alpha);
  }

  private int process(final int alphaData) {
    return alphaData + alphaData;
  }
}