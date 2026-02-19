// "Add 'finally' block" "true-preview"
class Test {
  void foo() {
      try {
      } finally {
          <caret>
      }
  }
}