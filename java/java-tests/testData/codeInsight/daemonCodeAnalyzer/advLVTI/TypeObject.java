class TypeObject {
  void test() {
    var x =  new Object() {};
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'anonymous java.lang.Object'">x = new Object()</error>;
  }
}