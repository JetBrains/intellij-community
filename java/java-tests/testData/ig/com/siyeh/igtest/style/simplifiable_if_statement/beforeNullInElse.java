// "Replace 'if else' with '?:'" "true"

class Test {
    private String foo() {
      <caret>if (one == null) {
          return "fd";
      }
      return null;
    }
}