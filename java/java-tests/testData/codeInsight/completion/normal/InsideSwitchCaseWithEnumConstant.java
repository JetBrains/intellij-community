class A extends Base{
  void foo(B b){
    switch(b) {
        case FOO:
          b.<caret>
    }
  }
}

enum B{
  FOO, BAR, GOO
}

