class A extends Base{
  void foo(){
    B x = null;
    switch(x){
      GOO<caret>
    }
  }
}

enum B{
  FOO, BAR, GOO
}

