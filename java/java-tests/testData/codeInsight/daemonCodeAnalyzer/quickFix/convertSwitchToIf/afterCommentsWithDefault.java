// "Replace 'switch' with 'if'" "true-preview"
class Foo {
  Object foo(int x) {
      if (x == 1) { // not needed
          return null;
      } else if (x == 2) {// not needed
          return null;
      } else if (x == 4) {
          return "foo";
      }
      return null;
  }
}
