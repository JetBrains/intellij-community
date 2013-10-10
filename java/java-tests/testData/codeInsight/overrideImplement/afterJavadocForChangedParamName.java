import java.util.*;
abstract class IX<T>  {
  /**
   * @param t the param
   */
   abstract void foo(T t){}
}

class XXC extends IX<List<String>> {
    /**
     * @param strings the param
     */
    @Override
    void foo(List<String> strings) {
        
    }
}
