import java.util.concurrent.Callable;
class Test<T> {

  public <P> Test() {
  }

  {
    Callable<Test<String>> c  = <error descr="Raw constructor reference with explicit type parameters for constructor">Test::<String>new</error>;
    Callable<Test<String>> c1 = Test<String>::<String>new;
  }
}
