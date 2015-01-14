// "Replace with lambda" "true"
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class Test {

  void f(List<Status> r){
    List<Status> l = r.stream().filter(status -> stringContainingSort(status))
      .map(Status::new)
      .collect(Collectors.toList());
  }

  boolean stringContainingSort(Object text) {
    return true;
  }

  class Status {
    public Status(Status s) {}
  }
}