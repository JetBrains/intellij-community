public class Foo {
    void m() {
        assert is();<caret>
    }
  
    boolean is() {
      return false;
    }
}