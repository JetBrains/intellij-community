import java.util.concurrent.Callable;
class Test<T> {

  public <P> Test() {
  }

  {
    Callable<Test<String>> c  = <error descr="Bad return type in method reference: cannot convert Test to Test<java.lang.String>">Test::<String>new</error>;
    Callable<Test<String>> c1 = Test<String>::<String>new;
  }
}
