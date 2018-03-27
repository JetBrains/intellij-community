import java.io.*;

class X {
 void f() throws
            <warning descr="Duplicate throws">Exception</warning>,
            Exception {
 }
 void f2() throws
            Exception,
            <warning descr="There is a more general exception, 'java.lang.Exception', in the throws list already.">IllegalArgumentException</warning> {
 }
 void f3() throws
            FileNotFoundException,
            EOFException {
 }

     public void TTT() throws
     <warning descr="Duplicate throws">FileNotFoundException</warning>,
     <warning descr="Duplicate throws">EOFException</warning>,
     FileNotFoundException,
     EOFException {
     }

  /**
   * Execute.
   *
   * @throws IOException if file write is failed
   * @throws Throwable if any other problem occurred
   */
  void execute() throws IOException, Throwable {

  }
}