class A{
  String toHex(int i) {} 
  String toHex(short i) {}

  void f(){
    <caret>toHex('i');
  }
}
