
import java.util.Map;

class Test {

  public static void main(String[] args) {
    Map<Integer, Object> map = <error descr="Inferred type 'java.lang.Object' for type parameter 'V' is not within its bound; should implement 'java.lang.Comparable'">make()</error>;
  }


  public static <K extends Comparable, V extends Comparable> Map<K,V> make() {
    return null;
  }

}