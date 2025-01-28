import java.util.List;

class Sample {
  <T extends List<K>, K extends List<T>> T foo(){
    T t = foo().<error descr="Incompatible types. Found: 'K', required: 'T'">get</error>(0);
    K k = foo().<error descr="Incompatible types. Found: 'K', required: 'K'">get</error>(0);

    T t1 = foo().get(0).<error descr="Incompatible types. Found: 'T', required: 'T'">get</error>(0);

    String s  = foo();
    String s1 = foo().<error descr="Incompatible types. Found: 'K', required: 'java.lang.String'">get</error>(0);
    return null;
  }

  {
    foo();
  }
}