import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Throwable;
import java.net.Socket;

class Foo {
  void unchecked() {
    boolean b = true;
    try {
      try {
        System.out.println();
      } finally {
        b = false;
      }
    } finally {
      if (<warning descr="Condition 'b' is always 'false'">b</warning>) {
        System.out.println();
      }
    }
  }
  
  void checked(boolean flag) throws Throwable {
    Throwable throwable = new Throwable();
    boolean b = true;
    try {
      if (flag) {
        try {
          throw throwable;
        } finally {
          b = false;
        }
      }
    } finally {
      if (b) {
        System.out.println();
      }
    }
  }

  void justReturn(boolean flag) throws Throwable {
    boolean b = true;
    try {
      try {
        return;
      } finally {
        b = false;
      }
    } finally {
      if (<warning descr="Condition 'b' is always 'false'">b</warning>) {
        System.out.println();
      }
    }
  }
}
