import java.util.List;

class Foo {
  void test() {
    switch(1) {
      default -> throw new <caret>
    }
  }
}