// "Replace 'if else' with '?:'" "INFORMATION"
public class TestCompletion {

  public static <T, V> ParallelPipeline<T, V> test(T base, V newStage, T upstream) {
      return base != null ? new ParallelPipeline<>(base, newStage) : new ParallelPipeline<>(upstream, newStage);
  }

  private static class ParallelPipeline<T, V> {
    public ParallelPipeline(T p0, V p1) {
    }
  }
}

