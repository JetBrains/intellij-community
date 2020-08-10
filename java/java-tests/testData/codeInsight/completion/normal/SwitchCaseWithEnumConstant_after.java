class A extends Base{
  void foo(){
    B x = null;
    switch(x){
        case GOO:<caret>
    }
  }
}

enum B{
  FOO, BAR, GOO
}

