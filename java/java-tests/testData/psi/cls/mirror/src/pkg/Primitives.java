package pkg;

class Primitives  {
  public static final boolean TRUE = true;
  public static final boolean FALSE = false;

  @BooleanAnno(true) public static boolean TRUE() { return TRUE; }
  @BooleanAnno(false) public static boolean FALSE() { return FALSE; }

  public static final byte BYTE = 1;
  public static final char CHAR = '\'';
  public static final short SHORT = 42;
  public static final int INT = 42;
  public static final long LONG = 42L;

  @ByteAnno(1) @CharAnno('\\') @ShortAnno(42) @IntAnno(42) @LongAnno(42L)
  public static void m() { }
}

@interface BooleanAnno {
  boolean value();
}

@interface ByteAnno {
  byte value();
}

@interface CharAnno {
  char value();
}

@interface ShortAnno {
  short value();
}

@interface IntAnno {
  int value();
}

@interface LongAnno {
  long value();
}
