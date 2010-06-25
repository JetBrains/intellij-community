class a {
  void f(int[] c) {
    for (int i:c) {}
    for (<error descr="Incompatible types. Found: 'int', required: 'char'">char i:c</error>) {}
    for (double i:c) {}
    double[] db = null;
    for (<error descr="Incompatible types. Found: 'double', required: 'int'">int i:db</error>) {}
    for (double i:db) {}

    java.util.List list = null;
    for (<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">String i:list</error>) {}
    for (Object o:list) {}

    java.util.List<Integer> ct = null;
    for (Number n:ct) {}
    for (Object n:ct) {}
    for (Integer n:ct) {}
    for (<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.String'">String i:ct</error>) {}
    for (<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.util.List<java.lang.Integer>'">java.util.List<Integer> i:ct</error>) {}

    Object o = null;
    for (Object oi: (Iterable)o) {}


    for (<error descr="Incompatible types. Found: 'double', required: 'int'">int i:db</error>) {
      for (<error descr="Incompatible types. Found: 'java.lang.Object', required: 'int'">int p: list</error>) {}
    }
  }
}