class A{
 class B<T>{}
 <T> void foo(T a, B<T> b){}

 {
  <ref>foo("", new B());
 }
}