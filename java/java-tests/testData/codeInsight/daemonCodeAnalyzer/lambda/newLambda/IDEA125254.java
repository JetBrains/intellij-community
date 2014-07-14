import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

class Scratch
{
  public static void main(String[] args)
  {
    final ExecutorService threadPool = Executors.newCachedThreadPool();
    final List<EventObject> events = new ArrayList<>();
    final SoapInterface soap = new SoapInterface();

    events.stream()
      .map(event -> get(event).apply(soap, event))
      .map(threadPool::submit)
      .forEachOrdered(future -> {
        try
        {
          if (! future.get().isError())
          {

          }
          else
          {
            ;
          }
        }
        catch (InterruptedException | ExecutionException e)
        {
          e.printStackTrace();
        }
      });
  }

  public static BiFunction<SoapInterface, Object, Callable<Either<Object, Exception>>> get(Object o)
  {
    return (soap, event) -> toEither(
      () -> {
        return "value";
      }
    );
  }

  private static Callable<Either<Object, Exception>> toEither(Callable<Object> callable)
  {
    return () -> {
      try
      {
        return Either.value(callable.call());
      }
      catch (Exception e)
      {
        return Either.error(e);
      }
    };
  }

  public static class EventObject {  }

  public static class SoapInterface {  }

  public static class Either<V, E>
  {
    private V value;

    private E error;

    private Either(V value, E error)
    {
      this.value = value;
      this.error = error;
    }

    public static <V, E> Either<V, E> value(V value)
    {
      return new Either<>(value, null);
    }

    public static <V, E> Either<V, E> error(E error)
    {
      return new Either<>(null, error);
    }

    public V getValue()
    {
      return value;
    }

    public E getError()
    {
      return error;
    }

    public boolean isError()
    {
      return error != null;
    }
  }
}