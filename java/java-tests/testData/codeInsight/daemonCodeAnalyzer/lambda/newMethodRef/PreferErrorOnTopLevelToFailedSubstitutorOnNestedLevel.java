import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class MyTest {
  static Map<String, Meeting> getMeetingsById(List<Meeting> meetings){
    return meetings.stream()
      .<error descr="Incompatible types. Found: 'java.util.Map<java.lang.String,java.util.List<Meeting>>', required: 'java.util.Map<java.lang.String,Meeting>'">collect</error>(Collectors.groupingBy(Meeting::getId));
  }

}
class Meeting {
  String getId() {
    return null;
  }
}
