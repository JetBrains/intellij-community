class A {
  public void f(A a){}

  public void g(){
    B b = new B(){
      void h(){
        f(A.this);<caret>
      }
    };
  }

  private class B{}
}
