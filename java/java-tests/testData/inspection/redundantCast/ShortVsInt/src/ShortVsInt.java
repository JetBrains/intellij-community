class A{
  String toHex(int i) {} 
  String toHex(short i) {}

  void f(){
    String result = toHex((short)'i');
  }
}
