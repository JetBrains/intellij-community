public class Comment {
  void m(String s) {
    // before line
    /* before */ s = /* qualifier */ s. /* call */ replace(/* arg 1 */"a", /* arg 2 */ "b" /* arg 3 */) /* expr */; // end
    // after line
  }
}