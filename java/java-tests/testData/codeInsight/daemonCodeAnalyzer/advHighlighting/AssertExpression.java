// assert statement

class a {
  void f() {
    assert false : <error descr="'void' type is not allowed here">System.out.println()</error>;

    assert <error descr="Incompatible types. Found: 'int', required: 'boolean'">0</error>;
    assert <error descr="Incompatible types. Found: 'char', required: 'boolean'">'a'</error>;
    assert <error descr="Incompatible types. Found: 'java.lang.String', required: 'boolean'">""</error>; 
    assert <error descr="Incompatible types. Found: 'void', required: 'boolean'">f()</error>;
  }
}