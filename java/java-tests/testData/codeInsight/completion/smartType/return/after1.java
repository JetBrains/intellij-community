class A{
  class B{
    int fooo(){
    }
  }

  int fooo(){
    B b = null;

    return b.fooo();<caret>
  }
}