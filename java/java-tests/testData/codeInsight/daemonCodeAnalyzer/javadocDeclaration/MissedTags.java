/**
 * @author me
 */
abstract class Foo {
  /**
   * @param i this is a parameter
   */
  public abstract void foo(int i);
}

/**
 * @author me
 */
class Bar extends Foo {
  /**
   */
  public void foo(int i) {
    i++;
  }
}