class Base {
  {
    Descendant descendant = new Descendant();
      consume((Base) descendant);
  }

  private static void consume(Base value) {}
  private static void consume(Descendant value) {}
}
class Descendant extends Base {}