import java.io.IOException;

class Test {

  interface B<K, E extends Throwable> {
    K l(K k) throws E;
  }

  <R> void bar(B<R, IOException> b) {}

  <E extends Exception, T> T baz(T l) throws E {
    return null;
  }

  {
    bar(l -> baz(l));
    bar(this::baz);
  }
}
class Test1 {

  interface B<K, E extends Throwable> {
    K l(K k) throws E;
  }

  <R> void bar(B<R, IOException> b) {}

  class MyEx extends Exception{}
  
  <E extends MyEx, T> T baz(T l) throws E {
    return null;
  }

  {
    bar(l -> baz<error descr="'baz(java.lang.Object)' in 'Test1' cannot be applied to '(<lambda parameter>)'">(l)</error>);
    bar(<error descr="Unhandled exception: Test1.MyEx">this::baz</error>);
  }
}
