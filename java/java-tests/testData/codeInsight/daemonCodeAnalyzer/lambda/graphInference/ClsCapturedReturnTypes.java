import java.util.Map;
import java.util.stream.Collector;


class Bug {


  {
    Collector<String, ?, Map<String, Long>> stringTreeMapCollector = groupingBy(counting());
  }

  public static <T, D, M extends Map<String, D>> Collector<T, ?, M> groupingBy(Collector<? super T, ?, Long> downstream) {
    return null;
  }

  public static <Tc> Collector<Tc, ?, Long> counting() {
    return null;
  }


}
