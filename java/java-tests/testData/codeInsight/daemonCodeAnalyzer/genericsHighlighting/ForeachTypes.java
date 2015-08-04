class a {
  void f(int[] c) {
    for (int i:c) {}
    for (<error descr="Incompatible types. Found: 'char', required: 'int'">char i</error>:c) {}
    for (double i:c) {}
    double[] db = null;
    for (<error descr="Incompatible types. Found: 'int', required: 'double'">int i</error>:db) {}
    for (double i:db) {}
    for (<error descr="Incompatible types. Found: 'int', required: 'double'">int i</error>:db) {
      // highlight header event if body has problems
      <error descr="Incompatible types. Found: 'java.lang.String', required: 'int'">int di = "";</error>
    }

    java.util.List list = null;
    for (<error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.Object'">String i</error>:list) {}
    for (Object o:list) {}

    java.util.List<Integer> ct = null;
    for (Number n:ct) {}
    for (Object n:ct) {}
    for (Integer n:ct) {}
    for (<error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.Integer'">String i</error>:ct) {}
    for (<error descr="Incompatible types. Found: 'java.util.List<java.lang.Integer>', required: 'java.lang.Integer'">java.util.List<Integer> i</error>:ct) {}

    Object o = null;
    for (Object oi: (Iterable)o) {}


    for (<error descr="Incompatible types. Found: 'int', required: 'double'">int i</error> : db) {
      for (<error descr="Incompatible types. Found: 'int', required: 'java.lang.Object'">int p</error>: list) {}
    }

    for (int gjkh : <error descr="foreach not applicable to type 'int'">222</error>) {
    }
  }
}