// "Wrap with block" "true-preview"
class X {
  void foo(int i) {
    switch(i) {
        case 1 -> {
            System.out.println("foo");
            System.out.println("bar");
        }
    }
  }
}