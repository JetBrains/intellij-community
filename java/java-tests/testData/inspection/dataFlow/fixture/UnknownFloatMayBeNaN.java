class Fun {
  public static void main(String[] args) throws Exception {
    float f1 = Float.parseFloat("NaN");
    if (f1 == f1) {
      System.err.println("ELVIS LIVES!");
    }
    float f2 = Float.NaN;
    // Warning:  Condition 'f2 == f2' is always 'false'
    // Correct, but if you extract the assignment to a method the inspection flips
    if (<warning descr="Condition 'f2 == f2' is always 'false'">f2 == f2</warning>) {
      System.err.println("ELVIS LIVES!");
    }
  }

}