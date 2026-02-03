import java.io.Serializable;

class B {}
abstract class A {
  abstract Class<?> get();
  abstract Class<? extends Object> get1();
  abstract Class<? super Object> get2();

  abstract Class<? extends B> get3();
  abstract Class<? super B> get4();

  abstract Class<? extends Serializable> get5();
  abstract Class<? super Serializable> get6();

  {
    if (get()  == byte[].class);
    if (get1() == byte[].class);
    if (<error descr="Operator '==' cannot be applied to 'java.lang.Class<capture<? super java.lang.Object>>', 'java.lang.Class<byte[]>'">get2() == byte[].class</error>);
    if (<error descr="Operator '==' cannot be applied to 'java.lang.Class<capture<? extends B>>', 'java.lang.Class<byte[]>'">get3() == byte[].class</error>);
    if (<error descr="Operator '==' cannot be applied to 'java.lang.Class<capture<? super B>>', 'java.lang.Class<byte[]>'">get4() == byte[].class</error>);
    if (get5() == byte[].class);
    if (<error descr="Operator '==' cannot be applied to 'java.lang.Class<capture<? super java.io.Serializable>>', 'java.lang.Class<byte[]>'">get6() == byte[].class</error>);
  } 
}