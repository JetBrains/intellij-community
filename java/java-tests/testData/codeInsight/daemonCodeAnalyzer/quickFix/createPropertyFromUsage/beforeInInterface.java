// "Create property" "false"
interface I {}

class Main {
  void usage(I i) {
    i.<caret>setFoo("hello");
  }
}
