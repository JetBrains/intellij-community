import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class MyTest {
  static Map<String, Meeting> getMeetingsById(List<Meeting> meetings){
    return <error descr="Incompatible types. Required Map<String, Meeting> but 'collect' was inferred to R:
no instance(s) of type variable(s) A, A, K, R, T exist so that List<T> conforms to Meeting">meetings.stream()
      .collect(Collectors.groupingBy(Meeting::getId));</error>
  }

}
class Meeting {
  String getId() {
    return null;
  }
}
