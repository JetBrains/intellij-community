class A{
  String <error descr="Invalid return type">toHex</error>(int i) {<error descr="Missing return statement">}</error>
  String <error descr="Invalid return type">toHex</error>(short i) {<error descr="Missing return statement">}</error>

  void f(){
    String result = toHex((short)'i');
  }
}
