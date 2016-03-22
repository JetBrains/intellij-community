
import java.util.Map;

class Test {

  public static void main(String[] args) {
    <error descr="Incompatible types. Found: 'java.util.Map<java.lang.Integer,java.lang.Comparable>', required: 'java.util.Map<java.lang.Integer,java.lang.Object>'">Map<Integer, Object> map = make();</error>
  }


  public static <K extends Comparable, V extends Comparable> Map<K,V> make() {
    return null;
  }

}