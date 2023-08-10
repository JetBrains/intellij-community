class C {
  private static void fn(boolean cond) {
    <caret>if (cond) {
      // comment
    } else {
      
    }
  }
}