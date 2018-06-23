/// initalizers completion
import java.io.*;

public class a  {

  <error descr="Initializer must be able to complete normally">{
    throw new RuntimeException();
  }</error>

  static <error descr="Initializer must be able to complete normally">{
    try {
      throw new RuntimeException();
    }
    finally {
      throw new NullPointerException();
    }
  }</error>


}
class a2 {
  { 
    if (1==2) <error descr="Unhandled exception: java.io.IOException">throw new IOException();</error> 
  }

  <error descr="Unhandled exception: java.io.IOException">a2()</error> {}
}
class SocketException extends IOException {
}
class ConnectException extends SocketException {
}

class a3 {
  { 
    if (1==2) <error descr="Unhandled exception: SocketException">throw new SocketException();</error>
  }

  <error descr="Unhandled exception: SocketException">a3() throws ConnectException</error> {}
}

class b {

  { if (1==2) throw new SocketException(); }

  b() throws IOException  {}
}

class a4 {
    {
        if (true) {
            throw new Exception();
        }
    }
    a4() throws Exception {
    }
}

class a5 {
  int i = <error descr="Unhandled exception: java.lang.ClassNotFoundException">f</error>();

  int f() throws ClassNotFoundException {
    return 0;
  }
}
class a6 {
  int i = f();

  int f() throws ClassNotFoundException {
    return 0;
  }

  a6() throws ClassNotFoundException {
  }
}

class a7 {
<error descr="Initializer must be able to complete normally">{
            try {
                throw new RuntimeException();
            } finally {
            }
 }</error>
}

class MyRuntimeException extends RuntimeException {}

class GUIBase {
  GUIBase () throws MyRuntimeException {}
}

class GUI extends GUIBase {

}


class a8 {
 {
   assert true;
 }
 {
   assert false;
 }

}

class a9 {

  private static interface AnInterface {}

  public AnInterface getAnInterface() {
        return new AnInterface() {
            {
                new <error descr="Unhandled exception: java.io.FileNotFoundException">java.io.FileInputStream</error>("somefile");
            }
        };
    }
}

////////////////////////////////
class BadStatic {
    static String f() throws ClassNotFoundException {
        return null;
    }
	private static final String FOO = <error descr="Unhandled exception: java.lang.ClassNotFoundException">f</error>();

	public BadStatic()  throws ClassNotFoundException {
	}
}
class H {
    public H() throws FileNotFoundException {
    }

    static {
        if(true) {      
            <error descr="Unhandled exception: java.io.FileNotFoundException">throw new FileNotFoundException();</error>
        }
    }
}
