interface Probable {}
abstract static class Something implements Probable {
  public abstract void run();
}
class AX {
  void x() {
    Something s = () -> {};
  }
}
interface SomethingSub extends Something {}
record SomethingElse() implements Something {
  @Override
  public void run() {

  }
}
enum SomethingEnum extends Something {
  ;

  @Override
  public void run() {

  }
}