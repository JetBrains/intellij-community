// Exception is never thrown in method

import java.io.*;
import java.sql.*;

class a {
    private void f() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">IOException</warning> {
    }
    public void usef() throws Exception { 
        f(); //avoid unused f()
    }

    private void f2() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">IOException</warning> {
      try {
        throw new IOException();
      }
      finally {
        return;
      }
    }
    public final void f3() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">IOException</warning> {
    }
    public static void f4() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">IOException</warning> {
    }

    public void usef2() throws Exception { 
        f2(); //avoid unused f()
    }
    public void usef3() throws Exception { 
        f3(); //avoid unused f()
    }
    public void usef4() throws Exception { 
        f4(); //avoid unused f()
    }

}

final class Final {
  {
      new Object() {
          void f() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">IOException</warning> {}
      };
  }
  
  void f() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">IOException</warning> {}
  public void f1() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">IOException</warning> {}
  protected void f2() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">IOException</warning> {}
}



class a1 {
    a1() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">java.io.IOException</warning>, <warning descr="Exception 'java.sql.SQLException' is never thrown in the method">SQLException</warning>{
    }

}

class b1 extends a1 {
    b1() throws IOException, SQLException {
    }
}

////////////////////////////////
public class FooThrow
{
  final Foo foo = new Foo();  // Can throw FooException
  FooThrow() throws Foo {
  }
}
class Foo extends Exception {
    public Foo() throws Foo {
        throw new Foo();
    }
}
//////////////
class H {
    public H() throws FileNotFoundException {            
    }

    {
        if(true) {
            throw new FileNotFoundException();
        }
    }
}

class PossibleIdeaBugs implements java.io.Serializable {

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    }

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
    }


    private Object writeReplace() throws java.io.ObjectStreamException {
            return this;
    }

    private Object readResolve() throws java.io.ObjectStreamException {
        return null;
    }
}

