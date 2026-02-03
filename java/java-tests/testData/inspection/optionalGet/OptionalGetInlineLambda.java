import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class OptIssue {
  public static List<MyObject> getMyObject(List<EventObject> events){
    Predicate<EventObject> isPresent = event -> event.getMethod().isPresent();
    // No warning -- IDEA-224757
    Function<EventObject, MyObject> getItem = e -> e.getMethod().get();
    return events.stream().filter(isPresent).map(getItem).collect(Collectors.toList());
  }

  private static class MyObject {
  }

  private static class EventObject {
    public Optional<MyObject> getMethod() {
      return null;
    }
  }
}