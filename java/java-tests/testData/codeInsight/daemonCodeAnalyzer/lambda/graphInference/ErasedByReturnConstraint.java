import java.util.List;

class Bug
{

  void foo(List<I> futures, I1<Object> callable){
    futures.add(submit(callable));
  }

  <T> I<T> submit(I1<T> task){
    return null;
  }

  interface I<TI>{}
  interface I1<TI2>{}
}
