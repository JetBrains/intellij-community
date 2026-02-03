package extends_implements_reference;

public class Ferrari implements Car {
  public void start() {}
}
interface Car {
  void start();
}
class Factory<T extends Car> {
  T create();
}