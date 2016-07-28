class A extends Base{
  void foo(B b){
    switch(b) {
        case FOO:
          GOO<caret>
    }
  }
}

enum B{
  FOO, BAR, GOO
}

