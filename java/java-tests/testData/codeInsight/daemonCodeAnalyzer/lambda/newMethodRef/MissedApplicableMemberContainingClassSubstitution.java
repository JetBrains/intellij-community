import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class TestA {

  public static class Entity<K> {
    K id;
    public K getId() {
      return id;
    }
  }

  public static class EntityVo {}

  public static class Area extends Entity<Integer> {
  }

  public static class AreaVo {
    public AreaVo(Area area, String lang) {

    }
  }

  public static void main(String[] args) {
    String language = "da";
    List<Area> areas = new ArrayList<>();
    Map<Integer, AreaVo> areaLookup = areas.stream()
      .collect(Collectors.toMap(Area::getId, area -> new AreaVo(area, language)));
  }

}

class TestSimple {

  public static class Entity<K> {
    K id;
    public K getId() {
      return id;
    }
  }

  public static class Area extends Entity<Integer> {
  }

  public static <M> Set<M> toMap(Function<Area, M> keyMapper) {
    return null;
  }

  {
    Set<Integer> tMapCollector = toMap(Area::getId);
  }

}