class A extends Base{
  void foo(){
    B x = null;
    switch(x){
      case <caret>
    }
  }
}

enum B{
  A,
  B,
  C
}

