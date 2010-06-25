// cyclic inhertiance
import java.io.*;
import java.net.*;

<error descr="Cyclic inheritance involving 'Foo'">class Foo extends Foo</error> {
}


interface Foo1 extends <error descr="Cannot resolve symbol 'Bar'">Bar</error> {
    interface Bar {
    }
}


<error descr="Cyclic inheritance involving 'c1'">class c1 extends c2</error> {}
<error descr="Cyclic inheritance involving 'c2'">class c2 extends c1</error> {}



class a1 {
    class b extends a1 {
    }
}


class a {
 static class sb extends a {
   class c extends a {
     void f() {
       class d extends a {
       }
     }
   }
 }
 class b extends sb {
  class c extends a {
  }
 }
}
         