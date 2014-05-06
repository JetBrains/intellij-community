import java.util.List;
import java.util.stream.Collectors;

class Scratch {
  private List<Id> entries;

  public String encode() {
    return  entries.stream()
      .map(e -> String.valueOf(e.getId()))
      .collect(Collectors.joining("+"));
  }

  private static class Id {
    public long getId() {
      return 0;
    }
  }
}
