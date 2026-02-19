// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      // BEFORE STATEMENT
      /* INSIDE 0*/
      throw <caret>switch /*INSIDE 1*/ (7) {
          // BEFORE FIRST
          case 1 -> new /*INSIDE 2*/ RuntimeException("one"); // TRAILING
          // BETWEEN
          default -> {
              // BEFORE
              yield new /*INSIDE 3*/ RuntimeException("default"); // TRAILING
              // AFTER
          }
          // AFTER LAST
      };
      // AFTER STATEMENT
  }
}