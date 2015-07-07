/**
 * {<warning>@linked</warning>}
 */
class Foo {
  /**
   * @param i some param {<warning>@vaaalue</warning> #field}
   */
  void foo(int i) {}

  /**
   * {<warning>@linke</warning>}
   */
  int field;

  /**
   * Don't ever think of going here: {<warning>@link/login</warning>}
   */
  void m() {}
}