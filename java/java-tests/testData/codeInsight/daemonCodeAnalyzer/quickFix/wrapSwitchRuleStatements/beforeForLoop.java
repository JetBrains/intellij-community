// "Wrap with block" "true"
class X {
  void foo(int i) {
    switch(i) {
      case 1 -><caret> for(int i=0; i<10; i++) System.out.println(i);
      case 2 ->
    }
  }
}