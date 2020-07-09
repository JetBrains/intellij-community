enum Enum242390 {
  TYPE1(<error descr="Illegal forward reference">Enum242390.TYPE2</error>),
  TYPE2(TYPE1);

  static final String C1 = Enum242390.D;
  static final String C2 = <error descr="Illegal forward reference">D</error>;
  static final String D = "";

  private final Enum242390 next;
  Enum242390(Enum242390 next) {
    this.next = next;
  }
}

enum Enum242390_2 {
  A(Enum242390_2.D);

  Enum242390_2(String s) { }

  static final String D = "";
}