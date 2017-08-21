// "Replace EnumSet.of().stream() with Stream.of()" "true"

import java.util.EnumSet;
import java.util.List;

import static java.util.stream.Collectors.toList;


public class Tests {
  interface HasCode {
    default String getCode() {return this.toString();}
  }

  enum Status implements HasCode {
    Approved, Pending, Stored
  }

  void test() {
    List<String> list = EnumSet.of(Status.Approved, (Status.Pending), Status.Stored).st<caret>ream()
      .map(HasCode::getCode).collect(toList());
  }
}
