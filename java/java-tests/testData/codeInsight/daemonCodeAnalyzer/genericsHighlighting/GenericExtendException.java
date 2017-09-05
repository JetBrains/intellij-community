class T extends Exception {}
class E<T> extends <error descr="Generic class may not extend 'java.lang.Throwable'">Error</error> {}
class M<T2 extends Exception> extends <error descr="Generic class may not extend 'java.lang.Throwable'">T</error> {}
class Ex<X,Y> extends <error descr="Generic class may not extend 'java.lang.Throwable'">Throwable</error> {}

class Outer<T> {
  public class Inner extends <error descr="Generic class may not extend 'java.lang.Throwable'">Throwable</error> {}
}