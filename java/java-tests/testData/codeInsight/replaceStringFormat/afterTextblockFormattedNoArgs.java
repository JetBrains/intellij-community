// "Fix all 'Redundant call to 'String.format()'' problems in file" "true"
package java.lang;

class String {
  String(Object original) {}
  public native String formatted(Object... args);
}

class Main {
  static {
      /* 6 */
      String s = (/* 1 */ new /* 2 */ String(/* 3 */"""
          Hello, World!
          """/* 4 */)/* 5 */);
    String s1 = new String("""
        %s, %s!
        """).formatted("Hello", "World");
    String s = (/* 1 */ new /* 2 */ String(/* 3 */"""
        Hello, World!%n
        """/* 4 */)/* 5 */)./* 6 */formatted();
  }
}
