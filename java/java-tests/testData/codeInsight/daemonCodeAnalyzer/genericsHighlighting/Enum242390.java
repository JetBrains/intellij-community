enum Enum242390 {
  TYPE1(<error descr="Illegal forward reference">Enum242390.TYPE2</error>),
  TYPE2(TYPE1);

  private final Enum242390 next;
  Enum242390(Enum242390 next) {
    this.next = next;
  }
}