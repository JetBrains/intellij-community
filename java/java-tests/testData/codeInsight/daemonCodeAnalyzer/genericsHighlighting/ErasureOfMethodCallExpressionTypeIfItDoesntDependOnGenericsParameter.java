class A<T> {
   <K> T foo(K p) {return null;}
}
class B<M> extends A<M> {
   <P> M foo(P p) {
     return super.foo(p);
   }
}