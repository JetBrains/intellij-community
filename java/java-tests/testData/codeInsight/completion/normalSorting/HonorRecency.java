public class Foo {
  
  void foo(Zoo z) {
    z.<caret>
  }
}

class Zoo {
  void setText() {}
  void setOurText() {}
}