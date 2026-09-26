class A{
  String toHex(int i) {<error descr="Missing return statement">}</error> 
  String toHex(short i) {<error descr="Missing return statement">}</error>

  void f(){
    String result = toHex((short)'i');
  }
}
