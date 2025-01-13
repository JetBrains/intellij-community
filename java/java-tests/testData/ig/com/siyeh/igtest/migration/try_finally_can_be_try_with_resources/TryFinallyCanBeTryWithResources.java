package com.siyeh.igtest.migration.try_finally_can_be_try_with_resources;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.*;

class TryFinallyCanBeTryWithResources {

  public void read1() throws IOException {
    final InputStream stream = new InputStream() {
      @Override
      public int read() throws IOException {
        return 0;
      }
    };
    <warning descr="'try' can use automatic resource management">try</warning> {
      stream.read();
    } finally {
      stream.close();
    }
  }

  public void write2() throws IOException {
    final InputStream stream = new InputStream() {
      @Override
      public int read() throws IOException {
        return 0;
      }
    };
    try {
      stream.read();
    } finally {
      System.out.println(stream);
      stream.close();
    }
  }

  public void write3() throws IOException {
    InputStream in  = true ? new FileInputStream("null") : null;
    try {
      byte[] magicNumber = new byte[2];
      in.mark(2);
      in.read(magicNumber);
      in.reset();
      if (false) {
        in = new FileInputStream("in"); // var can't be (implicitly final) resource var, because it is reassigned here
      }
    } finally {
      in.close();
    }
  }

  public void read4() throws IOException {
    FileInputStream fileInputStream = null;
    FileInputStream bufferedInputStream = null;
    try {
      fileInputStream = new FileInputStream("s");
      bufferedInputStream = null; // don't report, one of the vars is reassigned
      bufferedInputStream = new FileInputStream("fileInputStream");
    } finally {
      bufferedInputStream.close();
      fileInputStream.close();
    }
  }

  public void resourceListExists() throws IOException {
    FileInputStream f1 = new FileInputStream("1");
    <warning descr="'try' can use automatic resource management">try</warning> (FileInputStream f2 = new FileInputStream("2");/**/) {

    } finally {
      f1.close();
    }
  }
}

class Java9 {
  void test() throws FileNotFoundException {
    PrintStream printStream = new PrintStream("");
    printStream.print(false);
    <warning descr="'try' can use automatic resource management">try</warning> {
      printStream.print(true);
    } finally {
      printStream.close();
    }
  }
}

class FinallyContainsTry {
  void test() throws FileNotFoundException {
    PrintStream printStream = null;
    try {
      printStream = new PrintStream("");
    printStream.print(true);
    } finally {
      if (printStream != null) {
        try {
          printStream.close();
          System.out.println("USEFUL");
        } catch (Exception e) {}
      }
    }
  }
}

class NonResourceBeforeTry {
  public static String test(String str) throws IOException {
    final InputStream stream = new ByteArrayInputStream(str.getBytes());
    final StringBuilder res = new StringBuilder();
    try {
      int entry;
      while ((entry = stream.read()) > -1) {
        res.append(entry).append("\n");
      }
    }
    finally {
      stream.close();
    }

    return res.toString();
  }
}

class AutoCloseableResourceInitializedInFinally {
  interface MyAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }

  native MyAutoCloseable create();

  private void simple() {
    try {
      System.out.println(1);
    } finally {
      MyAutoCloseable closeable = create();
      closeable.close();
    }
  }

  private void IfStatement() {
    try {
      System.out.println(1);
    } finally {
      MyAutoCloseable closeable = create();
      if (closeable != null) {
        closeable.close();
      }
    }
  }

  private void finallyInsideFinallySimple() {
    try {
      System.out.println(1);
    }
    finally {
      MyAutoCloseable closeable = create();
      <warning descr="'try' can use automatic resource management">try</warning> {
        System.out.println(1);
      } finally{
        closeable.close();
      }
    }
  }

  private void finallyInsideFinallyIfStatement() {
    try {
      System.out.println(1);
    }
    finally {
      MyAutoCloseable closeable = create();
      <warning descr="'try' can use automatic resource management">try</warning> {
      } finally{
        if (closeable != null) {
          closeable.close();
        }
      }
    }
  }
}

class AutoCloseableResourceInLambdaOrAnonymousClass {
  interface MyAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }

  native MyAutoCloseable create();

  private void anonymousClassDifferentPlace() throws Exception  {
    MyAutoCloseable closeable = create();
    MyAutoCloseable closeable2 = new MyAutoCloseable() {
      @Override
      public void close() {
        try {
          System.out.println(1);
        } finally {
          closeable.close();
        }
      }
    };
  }

  private MyAutoCloseable getDelegate(MyAutoCloseable orig) {
    MyAutoCloseable result = null;
    MyAutoCloseable another = create();
    try {
      result = new MyAutoCloseable() {
        @Override
        public void close() {
          try {
            orig.close();
          } finally {
            another.close();
          }
        }
      };
      return result;
    } catch (Throwable e) {
      another.close();
    }
    return null;
  }

  private void anonymousClassExternalDeclaration()  throws Exception  {
    MyAutoCloseable closeable2 = new MyAutoCloseable() {
      @Override
      public void close() {
        MyAutoCloseable closeable = create();
        <warning descr="'try' can use automatic resource management">try</warning> {
          System.out.println(1);
        } finally {
          closeable.close();
        }
      }
    };
  }

  private void anonymousClassSamePlaceDeclaration() throws Exception {
    MyAutoCloseable closeable2 = new MyAutoCloseable() {
      @Override
      public void close() {
        MyAutoCloseable closeable = create();
        <warning descr="'try' can use automatic resource management">try</warning> {
          System.out.println(1);
        } finally {
          closeable.close();
        }
      }
    };
  }

  private void lambdaFunctionExternalUsageDeclaration() {
    AutoCloseable closeable = create();
    Runnable r = () -> {
      try {
        System.out.println(1);
      }
      finally {
        try {
          closeable.close();
        }
        catch (Exception e) {
        }
      }
    };
  }

  private void lambdaFunctionSamePlaceDeclaration() {
    Runnable r = () -> {
      AutoCloseable closeable = create();
      <warning descr="'try' can use automatic resource management">try</warning> {
        System.out.println(1);
      }
      finally {
        try {
          closeable.close();
        }
        catch (Exception e) {
        }
      }
    };
  }
}

class ExtraUsageOfAutoCloseableInFinally {
  interface MyAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }

  native MyAutoCloseable create();

  public void secondCall() throws Exception {
    MyAutoCloseable first = create();
    MyAutoCloseable second = create();
    try {
      System.out.println(first.hashCode());
    } finally {
      System.out.println(second.hashCode());
      first.close();
    }
  }

  public void respectsIfStatement() throws Exception {
    MyAutoCloseable first = create();
    <warning descr="'try' can use automatic resource management">try</warning> {
      System.out.println(first.hashCode());
    } finally {
      if (first != null) {
        first.close();
      }
    }
  }

  public void methodAccessInIfStatement() throws Exception {
    MyAutoCloseable first = create();
    try {
      System.out.println(first.hashCode());
    } finally {
      if (first.toString() != null) {
        first.close();
      }
    }
  }
}