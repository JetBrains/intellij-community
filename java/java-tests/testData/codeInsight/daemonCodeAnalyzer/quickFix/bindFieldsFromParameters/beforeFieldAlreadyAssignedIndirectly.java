// "Bind constructor parameters to fields" "false"

class Test {
  private final String value;

  Test(String <caret>input) {
    String processed = input.toUpperCase();
    this.value = processed;
  }
}
