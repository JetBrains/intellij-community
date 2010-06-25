// unhandled exception when messing with finally block

import java.io.*;
class a  {
  void f1(int i) {
    try {
      new FileReader("");
    }
    finally {
      <error descr="Unhandled exception: java.lang.ClassNotFoundException">throw new ClassNotFoundException();</error>
    }
  }

  void f2(int i) {
    try {
      <error descr="Unhandled exception: java.io.FileNotFoundException">new FileReader("")</error>;
    }
    finally {
      if (i==4) <error descr="Unhandled exception: java.lang.ClassNotFoundException">throw new ClassNotFoundException();</error>
    }
  }

  void f3(int i) {
    try {
      <error descr="Unhandled exception: java.io.FileNotFoundException">new FileReader("")</error>;
    }
    finally {
      if (i==1) return;
    }
  }

  void f4(int i) {
    try {
      <error descr="Unhandled exception: java.io.FileNotFoundException">new FileReader("")</error>;
    }
    finally {
      if (i==1) <error descr="Unhandled exception: java.io.FileNotFoundException">throw new FileNotFoundException();</error>
    }
  }

  void cf1(int i) {
    try {
      new FileReader("");
    }
    catch (FileNotFoundException e) {
    }
    finally {
      if (1==1) return;
    }
  }

  void cf2(int i) {
    try {
      new FileReader("");
    }
    finally {
      while (1==1) return;
    }
  }
  void foo(OutputStream out, byte[] data) throws IOException {
    out.write(data);
  }

    public void swallow() {
        try {
            throw new Exception("Hello World! I'm Checked Exception and must be declared!");
        } catch (Exception e) {
            throw e;
        } finally {
            return;
        }
    }
    public void spitout() {
        try {
            throw new Exception("Hello World! I'm Checked Exception and must be declared!");
        } catch (Exception e) {
             <error descr="Unhandled exception: java.lang.Exception">throw e;</error>
        } finally {
            //return;
        }
    }

}

