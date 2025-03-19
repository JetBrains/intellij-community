interface Probable {}
interface Something<caret> extends Probable {
  void run();
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
enum SomethingEnum implements Something {
  ;

  @Override
  public void run() {

  }
}