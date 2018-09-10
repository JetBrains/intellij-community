// "Simplify boolean expression" "true"
class A {
  public static void m(boolean fullSearch, boolean partialSearch) {


    if (!partialSearch) {
      return;
    }

    String str
      = fullSearch ? "str1"
                   : <warning descr="Condition 'partialSearch' is always 'true'"><caret>partialSearch</warning> ? "str2 " + "str3" // comment
                                   : null;
  }
}