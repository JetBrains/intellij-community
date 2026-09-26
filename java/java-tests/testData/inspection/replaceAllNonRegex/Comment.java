public class Comment {
  void m(String s) {
    // before line
    /* before */ s = /* qualifier */ s. /* call */ <warning descr="Call to 'replaceAll()' with a non-regex pattern can be replaced with 'replace()'"><caret>replaceAll</warning>(/* arg 1 */"a", /* arg 2 */ "b" /* arg 3 */) /* expr */; // end
    // after line
  }
}