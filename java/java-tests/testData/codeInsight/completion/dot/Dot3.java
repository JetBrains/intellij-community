class Dot3{
  class A{
    int a = 0;
    int foo(){
  }
  }
  static {
    
    Object a = new A(){
      static int foo(){
        int c = super.<caret>
      }
    };
  }
}
