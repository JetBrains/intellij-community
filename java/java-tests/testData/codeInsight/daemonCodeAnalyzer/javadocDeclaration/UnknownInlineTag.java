/**
 * {<warning descr="Wrong tag 'linked'">@linked</warning>}
 */
class Foo {
  /**
   * @param i some param {<warning descr="Wrong tag 'vaaalue'">@vaaalue</warning> #field}
   */
  void foo(int i) {}

  /**
   * {<warning descr="Wrong tag 'linke'">@linke</warning>}
   */
  int field;

  /**
   * Don't ever think of going here: {<warning descr="Wrong tag 'link/login'">@link/login</warning>}
   */
  void m() {}
}