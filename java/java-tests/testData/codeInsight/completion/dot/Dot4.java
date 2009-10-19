class Dot4{
  class A{
    static int a = 0;
    int foo(){
    }
  }
  static {
    int a = new A().<caret>
  }
}
