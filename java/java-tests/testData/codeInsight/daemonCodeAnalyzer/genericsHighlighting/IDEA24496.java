import java.util.List;
import java.util.ArrayList;

class GenericsError {

  public <T> List<List<T>> method(List<T> list) {
    List<List<T>> retVal = new ArrayList<List<T>>();
    retVal.add(list);
    return retVal;
  }

  public List<List<?>> otherMethod() {
    List<?> list = null;
    <error descr="Incompatible types. Found: 'java.util.List<java.util.List<capture<?>>>', required: 'java.util.List<java.util.List<?>>'">List<List<?>> result = method(list);</error>
    return result;
  }
}