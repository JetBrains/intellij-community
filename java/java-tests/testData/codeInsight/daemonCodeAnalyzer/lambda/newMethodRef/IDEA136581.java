import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.*;

class Main {
  public static void main(String... args) {
    final List<TestReferences> entities = new ArrayList<>();
    entities.add(new TestReferences(1, 2));
    entities.add(new TestReferences(3, 4));

    process(entities);
  }

  private static Map<Integer, Set<Integer>> process(List<? extends References> entities) {
    return entities.stream()
      .collect(groupingBy(References::getGroupById,
                          mapping(References::getOtherId, toSet())));
  }

  public static interface References {
    int getGroupById();

    int getOtherId();
  }

  public static class TestReferences implements References {
    private int groupById;

    private int otherId;

    public TestReferences(int groupById, int otherId) {
      this.groupById = groupById;
      this.otherId = otherId;
    }

    @Override
    public int getGroupById() {
      return groupById;
    }

    public void setGroupById(int groupById) {
      this.groupById = groupById;
    }

    @Override
    public int getOtherId() {
      return otherId;
    }

    public void setOtherId(int otherId) {
      this.otherId = otherId;
    }
  }
}
