// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class SimpleExpressionsWithComments {
  String foo(int n) {
    return switch (n) {
      //comment1
      //comment2
      //comment3
      case 1, 3 -> "A"; //comment4
      case 2 -> "B";
    };
  }
}