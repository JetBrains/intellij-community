class B{
 void b(){};
}

interface E{
 void e();
}

class C extends B{
 void c(){}
}

class D extends B implements E{
 void d(){}
 void e(){}
}


class A<Z>{
 <T> T foo(T t, A<T> a){ return null; }

 {
  foo(new C(), new A<D>()).<ref>b();
 }
}