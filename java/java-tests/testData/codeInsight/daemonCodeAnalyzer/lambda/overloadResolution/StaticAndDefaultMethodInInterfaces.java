interface ILog {
  default void log() {
    System.out.println("ILog");
  }

  interface ILogStatic {
    public static void log() {
      System.out.println("ILog");
    }
  }
}

interface ILogAbstract {
  public void log();
}

abstract class LogStatic {
  public static void log() {
    System.out.println("Log");
  }
}

abstract class LogImplement {
  public void log() {
    System.out.println("Log");
  }
}

abstract class LogAbstract {
  public abstract void log();
}

interface A
{
  static void foo(){}
}


interface I {
  default void f() {}
}

interface J {
  static void f() {}
}

class IJ implements I, J {}

// must not highlight!
class JI implements J, I {}


interface B extends A
{

  // must not highlight!
  static int foo(){ return 1; }
}


// highlight
//cannot override instance method
<error descr="Static method 'log()' in 'LogStatic' cannot override instance method 'log()' in 'ILog'">class MyLogger1 extends LogStatic implements ILog</error> {

}

class MyLogger2 extends LogImplement implements ILog.ILogStatic {

}


// highlight
// test.com.MyLogger22  must either be declared abstract or implement abstract method
<error descr="Class 'MyLogger22' must either be declared abstract or implement abstract method 'log()' in 'LogAbstract'">class MyLogger22 extends LogAbstract implements ILog.ILogStatic</error> {

}

// must not be highlighted
class MyLogger3 implements ILog.ILogStatic, ILog {

}

// highlight
//cannot override instance method
<error descr="Static method 'log()' in 'LogStatic' cannot override instance method 'log()' in 'ILogAbstract'">class MyLogger4 extends LogStatic implements ILogAbstract</error> {

}

interface MyLogger5 extends ILogAbstract, ILog.ILogStatic {

}

interface MyLogger6 extends ILog, ILog.ILogStatic {

}

interface Mylogger7 extends ILog.ILogStatic{
  // must not be highlighted
  default void log() {
    System.out.println("ILog");
  }
}
interface Mylogger8 extends ILog{
  default void log() {
    System.out.println("ILog");
  }
}

interface Mylogger9 extends ILog{
  // highlight
  // cannot override instance method
  <error descr="Static method 'log()' in 'Mylogger9' cannot override instance method 'log()' in 'ILog'">public static void log()</error> {
    System.out.println("ILog");
  }
}

interface Mylogger10 extends ILog.ILogStatic{
  public static void log() {
    System.out.println("ILog");
  }
}

