// "Make 'e' not final" "false"

class C {
  static {
    try {
      throw new Exception();
    }
    catch (RuntimeException | IOException e) {
      <caret>e = null;
    }
  }
}