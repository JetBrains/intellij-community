public class Foo {
    void m() {
        if (is()) {
            <caret>
        }
    }
  
    boolean is() {
      return false;
    }
}