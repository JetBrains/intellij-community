import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

class Reducer {
  private final Map<String, Dto> dtos = new HashMap<>();

  private void publishToOpxl() {
    Collector<Intermediate, ?, Map<String, Integer>> reducer =
      Collectors.groupingBy(Intermediate::name, Collectors.summingInt(Intermediate::value));

    Map<String, Map<String,Integer>> green = dtos.values()
      .stream()
      .flatMap(dto -> dto.getNestedThings()
        .values()
        .stream()
        .map(ppd -> new Intermediate(dto.getName(),
                                     ppd.getKey(),
                                     ppd.getValue())))
      .collect(
        Collectors.groupingBy(
          Intermediate::key,
          reducer
        ));

    Map<String, Map<String,Integer>> red = dtos.values()
      .stream()
      .flatMap(dto -> dto.getNestedThings()
        .values()
        .stream()
        .map(ppd -> new Intermediate(dto.getName(),
                                     ppd.getKey(),
                                     ppd.getValue())))
      .collect(
        Collectors.groupingBy(
          Intermediate::key,
          Collectors.groupingBy(Intermediate::name, Collectors.summingInt(Intermediate::value))
        ));

  }

  public static class Intermediate {
    public final String name;
    public final String key;
    public final int value;

    public Intermediate(String name, String key, int value) {
      this.name = name;
      this.key = key;
      this.value = value;
    }

    public String name() {
      return name;
    }

    public String key() {
      return key;
    }

    public int value() {
      return value;
    }
  }
}

interface Dto {

  String getName();

  Map<String, Dto2> getNestedThings();
}

interface Dto2 {
  String getKey();

  int getValue();
}