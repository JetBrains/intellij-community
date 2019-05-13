public class Util {
  int goo() {
    try (Object foo = bar()) {
      
    }
    <caret>
  }
}
