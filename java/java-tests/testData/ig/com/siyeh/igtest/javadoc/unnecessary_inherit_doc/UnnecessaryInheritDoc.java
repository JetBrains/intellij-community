/**
 * <warning descr="'{@inheritDoc}' is not valid on classes">{@inheritDoc}</warning> comment comment
 */
class X implements I {

  /**
   * <warning descr="'{@inheritDoc}' is not valid on fields">{@inheritDoc}</warning> comment comment
   */
  private String s;

  /**
   * <warning descr="'{@inheritDoc}' is not valid on constructors">{@inheritDoc}</warning> comment comment
   */
  X() {}

  /**
   * <warning descr="Javadoc comment containing only '{@inheritDoc}' is unnecessary">{@inheritDoc}</warning>
  * @throws FooException comment
  */
  public void f() {}

  /**
   *
   * @param i <warning descr="Javadoc comment containing only '{@inheritDoc}' is unnecessary">{@inheritDoc}</warning>
   * @return <warning descr="Javadoc comment containing only '{@inheritDoc}' is unnecessary">{@inheritDoc}</warning>
   * @throws IllegalArgumentException {@inheritDoc}
   * @throws Exception <warning descr="Javadoc comment containing only '{@inheritDoc}' is unnecessary">{@inheritDoc}</warning>
   */
  @Override
  public int plus(int i) throws Exception {
    return 0;
  }

  /**
   * <warning descr="No super method found to inherit Javadoc from">{@inheritDoc}</warning>
   */
  void unknown() {}
}
interface I {
  /***/ void f();

  /**
   * hasta
   * @param i la
   * @return vista
   * @throws Exception baby
   */
  int plus(int i) throws Exception;
}