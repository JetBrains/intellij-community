public class SimplePositive {
  void m(String s) {
    s = s.<warning descr="Call to 'replaceAll()' with a non-regex pattern can be replaced with 'replace()'"><caret>replaceAll</warning>("abc", "x");
    s = s.<warning descr="Call to 'replaceAll()' with a non-regex pattern can be replaced with 'replace()'">replaceAll</warning>("-", "/");
    s = s.<warning descr="Call to 'replaceAll()' with a non-regex pattern can be replaced with 'replace()'">replaceAll</warning>(" ", "_");
    s = s.<warning descr="Call to 'replaceAll()' with a non-regex pattern can be replaced with 'replace()'">replaceAll</warning>("]", "a");
  }
}