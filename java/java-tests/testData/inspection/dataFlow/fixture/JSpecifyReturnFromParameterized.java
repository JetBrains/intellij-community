import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.concurrent.Callable;

@NullMarked
class ReturnFromParameterizedNullMarked {
  public <R extends Serializable> void fetchOneDelegate() throws Exception {
    Callable<R> callable = new Callable<R>() {
      @Override
      public R call() throws Exception {
        return <warning descr="'null' is returned by the method declared as @NullMarked">null</warning>;
      }
    };

    Callable<R> callable2 = () -> <warning descr="'null' is returned by the method declared as @NullMarked">null</warning>;
  }

  public <R> void fetchOneDelegate2() throws Exception{
    Callable<R> callable = new Callable<R>() {
      @Override
      public R call() throws Exception {
        return <warning descr="'null' is returned by the method declared as @NullMarked">null</warning>;
      }
    };
    Callable<R> callable2 = () -> <warning descr="'null' is returned by the method declared as @NullMarked">null</warning>;
    R r = new Callable<R>() {
      @Override
      public R call() throws Exception {
        return <warning descr="'null' is returned by the method declared as @NullMarked">null</warning>;
      }
    }.call();
    R r2 = ((Callable<R>) () -> <warning descr="Function may return null, but it's not allowed here">null</warning>).call();
  }
}