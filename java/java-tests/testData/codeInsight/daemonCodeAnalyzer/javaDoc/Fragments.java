/**
 * @see <warning descr="Label expected after URI fragment">##id</warning>
 * @see <warning descr="Label expected after URI fragment">C##id</warning>
 * @see ##id label
 * @see C##id label
 *
 * {@link <warning descr="Label expected after URI fragment">##id</warning>}
 * {@link <warning descr="Label expected after URI fragment">C##id</warning>}
 * {@link ##id label}
 * {@link C##id label}
 */
class C {
  int f;

  /**
   * <p id=id></p>
   */
  void foo() {}
}