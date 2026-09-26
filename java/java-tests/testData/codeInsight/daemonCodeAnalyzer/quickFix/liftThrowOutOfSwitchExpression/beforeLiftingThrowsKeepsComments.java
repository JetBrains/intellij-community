// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      // BEFORE STATEMENT
      return /* INSIDE 0*/ <caret>switch /*INSIDE 1*/ (7) {
          // BEFORE FIRST
          case 1 -> throw new /*INSIDE 2*/ RuntimeException("one"); // TRAILING
          // BETWEEN
          default -> {
              // BEFORE
              throw new /*INSIDE 3*/ RuntimeException("default"); // TRAILING
              // AFTER
          }
          // AFTER LAST
      };
      // AFTER STATEMENT
  }
}