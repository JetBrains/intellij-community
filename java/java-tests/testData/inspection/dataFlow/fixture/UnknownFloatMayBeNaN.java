class Fun {
  public static void main(String[] args) throws Exception {
    float f1 = Float.parseFloat("NaN");
    if (<warning descr="Condition 'f1 == f1' is always 'false'">f1 == f1</warning>) {
      System.err.println("ELVIS LIVES!");
    }
    float f3 = getFloat();
    if (f3 == f3) {
      System.out.println("ELVIS LIVES!");
    }
    float f2 = Float.NaN;
    // Warning:  Condition 'f2 == f2' is always 'false'
    // Correct, but if you extract the assignment to a method the inspection flips
    if (<warning descr="Condition 'f2 == f2' is always 'false'">f2 == f2</warning>) {
      System.err.println("ELVIS LIVES!");
    }
  }

  private static native float getFloat();
}