interface A<T extends B<? extends T>> { }
interface B<T extends A<?>> { }

class C {
  void foo(A<?> x){
    <error descr="Incompatible types. Found: 'A<capture<?>>', required: 'A<? extends B<? extends A<?>>>'">A<? extends B<? extends A<?>>>  y =  x;</error>
  }
}