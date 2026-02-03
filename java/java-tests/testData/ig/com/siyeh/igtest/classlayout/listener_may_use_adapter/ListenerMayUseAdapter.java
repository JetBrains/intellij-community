class ListenerMayUseAdapter implements Listener {

  @Override
  public void one() {
  }
}
class ListenerMayUseAdapter2 implements MyListener {

  @Override
  public void one() {

  }

  @Override
  public void two() {

  }
}
class ListenerMayUseAdapter3 implements <warning descr="Class 'ListenerMayUseAdapter3' may extend 'GoodAdapter' instead of implementing 'GoodListener'">GoodListener</warning> {
  @Override
  public void one() {
  }

  @Override
  public void two() {
  }
}
interface Listener {
  default void one() {}
  default void two() {}
}
abstract class Adapter implements Listener {
  @Override
  public void one() {
  }

  @Override
  public void two() {
  }
}
interface MyListener {
  void one();
  void two();
}
@Deprecated
abstract class MyAdapter implements MyListener {
  @Override
  public void one() {
  }

  @Override
  public void two() {
  }
}
interface GoodListener {
  void one();
  void two();
}
abstract class GoodAdapter implements GoodListener {
  @Override
  public void one() {
  }

  @Override
  public void two() {
  }
}