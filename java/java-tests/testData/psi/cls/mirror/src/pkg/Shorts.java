package pkg;

class Shorts  {
  public static final short s = 1;

  @ShortAnno(1) public static short s() { return s; }
}

@interface ShortAnno {
  short value();
}
