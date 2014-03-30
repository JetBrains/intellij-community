import java.util.List;

abstract class StreamMain {
  public abstract  <T> Iterable<T> concat(final Iterable<? extends T>... iterables);
  public abstract  <T> Iterable<T> concat(final List<? extends T>... iterables);


  public final List<String> errorFixesToShow = null;
  public final List<String> inspectionFixesToShow = null;

  {
    concat(errorFixesToShow,   inspectionFixesToShow);
  }
}