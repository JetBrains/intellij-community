abstract class A<T, S extends T>
{
  abstract S bar();
  void foo(A<Runnable[], ? extends Cloneable[]> a){
    <error descr="Incompatible types. Found: 'capture<? extends java.lang.Cloneable[]>', required: 'java.lang.Runnable[]'">Runnable[] x = a.bar();</error>
  }
}

abstract class AB<T, S extends T>
{
  abstract S bar();
  void foo(AB<Runnable, ? extends Cloneable> a){
    Runnable x = a.bar();
  }
}

abstract class AC<T, S>
{
  abstract S bar();
  void foo(AC<Runnable[], ? extends Cloneable[]> a){
    <error descr="Incompatible types. Found: 'capture<? extends java.lang.Cloneable[]>', required: 'java.lang.Runnable[]'">Runnable[] x = a.bar();</error>
  }
}

