
class Main {
  {
    String.class.isRecord();
    <error descr="Usage of API documented as @since 17+">String.class.isSealed</error>();
  }
}