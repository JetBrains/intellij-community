/**
 * @author me
 */
abstract class Foo {
  /**
   * @param <warning descr="'@param i' tag description is missing">i</warning>
   * @param <warning descr="'@param j' tag description is missing">j</warning>
   */
  public abstract void foo(int i, int j);
}