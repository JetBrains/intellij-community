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
    List<List<?>> result = <error descr="Incompatible types. Found: 'java.util.List<java.util.List<capture<?>>>', required: 'java.util.List<java.util.List<?>>'">method</error>(list);
    return result;
  }
}