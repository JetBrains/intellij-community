// "Replace with lambda" "true"
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class Test {

  void f(List<Status> r){
    List<Status> l = r.stream().filter(new Pr<caret>edicate<Status>() {
      @Override
      public boolean test(Status status) {
        return stringContainingSort(status);
      }
    })
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