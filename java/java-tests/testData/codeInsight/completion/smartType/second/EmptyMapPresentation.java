import java.util.Map;

public class Foo {

  void foo(Map<String, String> map) {
    
  }

  void bar() {
    foo(em<caret>)
  }
}