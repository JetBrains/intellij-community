// sync statement

class a {
  void f() {

        synchronized (<error descr="Incompatible types. Found: 'null', required: 'java.lang.Object'">null</error>) {
        }
        synchronized (<error descr="Incompatible types. Found: 'int', required: 'java.lang.Object'">0</error>) {
        }
        synchronized (<error descr="Incompatible types. Found: 'char', required: 'java.lang.Object'">'a'</error>) {
        }
        synchronized (<error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Object'">true</error>) {
        }
        synchronized (<error descr="Incompatible types. Found: 'void', required: 'java.lang.Object'">System.out.println()</error> ) {
        }


  }

}