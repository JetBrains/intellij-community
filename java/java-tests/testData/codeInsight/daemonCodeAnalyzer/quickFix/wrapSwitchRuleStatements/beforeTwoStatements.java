// "Wrap with block" "true"
class X {
  void foo(int i) {
    switch(i) {
      case 1 ->
        System.out.println("foo");
        System<caret>.out.println("bar");
    }
  }
}