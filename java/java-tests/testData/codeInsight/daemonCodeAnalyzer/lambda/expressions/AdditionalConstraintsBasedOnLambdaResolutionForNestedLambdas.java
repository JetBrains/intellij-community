
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class Main {
  public static <T1, T2> T2 safeGet(T1 value,
                                    Function<? super T1, ? extends T2> extractor) {
    return value == null ? null : extractor.apply(value);
  }

  public static class Data {
    public java.util.Date date() {
      return new java.util.Date();
    }
  }

  public static void main(String[] args) {
    List<Data> list = Collections.emptyList();
    Map<Long, Data> map1 = list.stream().collect(Collectors.toMap(data -> data.date().getTime(), Function.identity()));
    Map<Long, Data> map2 = safeGet(list,
                                   li -> li.stream().collect(Collectors.toMap(data -> data.date().getTime(), Function.identity())));
  }
}