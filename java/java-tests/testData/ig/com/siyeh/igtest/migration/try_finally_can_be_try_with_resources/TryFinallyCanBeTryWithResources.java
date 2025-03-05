package com.siyeh.igtest.migration.try_finally_can_be_try_with_resources;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
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

  private void twoVariables() {
    MyAutoCloseable closeable1 = create();
    try {
      System.out.println(1);
    } finally {
      closeable1.close();
      MyAutoCloseable closeable2 = create();
      if (closeable2 != null) {
        closeable2.close();
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

  private void twoVariablesWithAnonymousClass() throws Exception  {
    MyAutoCloseable closeable1 = create();
    MyAutoCloseable closeable = new MyAutoCloseable() {
      @Override
      public void close() {
        MyAutoCloseable closeable2 = create();
        try {
          System.out.println(1);
        } finally {
          closeable1.close();
          closeable2.close();
        }
      }
    };
  }

  private void twoVariablesWithLambdaExpression() throws Exception  {
    AutoCloseable closeable1 = create();
    Runnable r = () -> {
      AutoCloseable closeable2 = create();
      try {
        System.out.println(1);
      }
      finally {
        try {
          closeable1.close();
          closeable2.close();
        }
        catch (Exception e) {
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

  public void expressionList() throws Exception {
    MyAutoCloseable first = create();
    try {
      System.out.println(first.hashCode());
    } finally {
      first.close();
      System.out.println(first.hashCode());
    }
  }
}

class NestedCatchSections {
  static class A extends RuntimeException {
    A(String message) {
      super(message);
    }
  }

  static class B extends RuntimeException {
    B(String message) {
      super(message);
    }
  }

  static class C extends RuntimeException {
    C(String message) {
      super(message);
    }
  }

  static class D extends RuntimeException {
    D(String message) {
      super(message);
    }
  }

  interface MyAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }

  void doubleTrySameVariables(InputStream stream) {
    try {
      System.out.println(1);
    } finally {
      try {
        stream.close();
      } catch (IOException e) {
      }
      try {
        stream.close();
      } catch (IOException e) {
      }
    }
  }

  void doubleTrySameExceptions(InputStream stream, InputStream stream2) {
    try {
      System.out.println(1);
    } finally {
      try {
        stream.close();
      } catch (IOException e) {
      }
      try {
        stream2.close();
      } catch (IOException e) {}
    }
  }


  void withPrefixInFinally(InputStream stream) {
    try {
      System.out.println(1);
    } finally {
      int x = 10;
      try {
        stream.close();
      } catch (IOException e) {
      }
    }
  }

  void duplicateExceptionsInOuterAndInnerCatch(InputStream stream) {
    <warning descr="'try' can use automatic resource management">try</warning> {
      stream.read();
    } catch (IOException e) {

    } finally {
      try {
        stream.close();
      } catch (IOException e) {
      }
    }
  }

  void disjointExceptionsInOuterAndInnerCatch(InputStream stream) {
    <warning descr="'try' can use automatic resource management">try</warning> {
      System.out.println(1);
    } catch (A | B e) {

    } finally {
      try {
        stream.close();
      } catch (C | IOException e) {

      }
    }
  }

  void disjointExceptionsInDoubleInnerCatch(InputStream stream, InputStream stream2) {
    try {
      System.out.println(1);
    }
    finally {
      try {
        stream.close();
      }
      catch (A | IOException e) {

      }
      try {
        stream2.close();
      }
      catch (B | IOException e) {

      }
    }
  }

  void variableUsedInSecondInnerTry(InputStream stream) {
    try {
      System.out.println(1);
    } finally {
      try {
        stream.close();
      }
      catch (Exception e) {
      }
      try {
        stream.close();
      } catch (Exception e) {
      }
    }
  }

  void nonFirstTryStatementsInFinallyBlock(InputStream stream) {
    try {
      System.out.println(1);
    } finally {
      System.out.println(1);
      try {
        stream.close();
      } catch (Exception e) {
      }
    }
  }

  void twoTryStatementsInFinallyBlock(InputStream stream, InputStream stream2) {
    try {
      System.out.println(1);
    } finally {
      try {
        stream.close();
      } catch (Exception e) {
      }
      try {
        stream2.close();
      } catch (Exception e) {
      }
    }
  }
}

class UsageOfDependentVariablesInFinally {
  private native InputStream createStream();

  void inFinally() throws IOException {
    InputStream stream = createStream();
    BufferedInputStream buffered = new BufferedInputStream(stream);
    try {
      System.out.println(buffered.read());
    } finally {
      System.out.println(buffered.read());
      stream.close();
    }
  }

  void inInnerCatchStatement() throws IOException {
    InputStream stream = createStream();
    BufferedInputStream buffered = new BufferedInputStream(stream);
    try {
      System.out.println(buffered.read());
    } finally {
      try {
        stream.close();
      } catch (IOException e) {
        buffered.read();
      }
    }
  }

  void respectsVariablesBefore() throws IOException {
    int x = 10;
    InputStream stream = createStream();
    <warning descr="'try' can use automatic resource management">try</warning> {
      System.out.println(x);
    }
    finally {
      stream.close();
      System.out.println(x);
    }
  }

  void variableBetweenTwoAutoCloseables() throws IOException {
    InputStream stream = createStream();
    BufferedInputStream buffered = new BufferedInputStream(stream);
    InputStream stream2 = createStream();
    try {
      System.out.println(buffered.read());
    }
    finally {
      System.out.println(buffered.read());
      stream.close();
      stream2.close();
    }
  }
}
