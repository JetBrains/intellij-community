import java.util.List;

class Sample {
  <T extends List<K>, K extends List<T>> T foo(){
    <error descr="Incompatible types. Found: 'K', required: 'T'">T t = foo().get(0);</error>
    <error descr="Incompatible types. Found: 'K', required: 'K'">K k = foo().get(0);</error>

    <error descr="Incompatible types. Found: 'T', required: 'T'">T t1 = foo().get(0).get(0);</error>

    String s  = foo();
    <error descr="Incompatible types. Found: 'K', required: 'java.lang.String'">String s1 = foo().get(0);</error>
    return null;
  }

  {
    foo();
  }
}