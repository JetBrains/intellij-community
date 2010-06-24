class c {
  void f() {
    Object o = null;
    if (<error descr="Incompatible types. Found: 'java.lang.Object', required: 'boolean'">o</error>) {
      return;
    }

    String str1 = "";
    String str2 = "";
    do {} 
    while (<error descr="Incompatible types. Found: 'java.lang.String', required: 'boolean'">str1 = str2</error>);

    int i8 = <error descr="Incompatible types. Found: 'java.lang.String', required: 'boolean'">"ff" + true</error> ? 1 : 2;

    assert <error descr="Incompatible types. Found: 'int', required: 'boolean'">0</error>;
    assert <error descr="Incompatible types. Found: 'char', required: 'boolean'">'a'</error>;
    assert <error descr="Incompatible types. Found: 'java.lang.String', required: 'boolean'">""</error>; 
    assert <error descr="Incompatible types. Found: 'void', required: 'boolean'">f()</error>;
  }
}