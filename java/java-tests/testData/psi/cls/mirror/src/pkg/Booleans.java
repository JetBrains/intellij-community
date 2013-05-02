package pkg;

class Booleans  {
  public static final boolean TRUE = true;
  public static final boolean FALSE = false;

  @BooleanAnno(true) public static boolean TRUE() { return TRUE; }
  @BooleanAnno(false) public static boolean FALSE() { return FALSE; }
}

@interface BooleanAnno {
  boolean value();
}