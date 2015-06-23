import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;


class Test {
  interface RunnableX extends Callable<String> {
    void run() throws Exception;

    default String call() throws Exception
    {
      run();
      return null;
    }
  }

  static void foo(RunnableX r){
    System.out.println(r);
  }
  static void foo(Callable<List<?>> c){
    System.out.println(c);
  }

  public void test() {
    <error descr="Ambiguous method call: both 'Test.foo(RunnableX)' and 'Test.foo(Callable<List<?>>)' match">foo</error>(()->  new ArrayList<Void>() );
  }

}

class Test1 {
  interface RunnableX extends Callable<List<?>> {
    void run() throws Exception;

    default List<?> call() throws Exception
    {
      run();
      return null;
    }
  }

  static void foo(RunnableX r){
    System.out.println(r);
  }
  static void foo(Callable<List<?>> c){
    System.out.println(c);
  }

  public void test() {
    foo(()->  new ArrayList<Void>() );
  }

}

