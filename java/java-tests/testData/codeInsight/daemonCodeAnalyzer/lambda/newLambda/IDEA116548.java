import java.util.*;

class Tmp
{
    interface Callable<V> {
      V run() throws Exception;
    }
  
    <T> List<T> foo(Callable<T> callable){ return null; }

    void test()
    {
        List<String> list = foo(()->{ throw new Error(); } );  // IntelliJ error
    }
}