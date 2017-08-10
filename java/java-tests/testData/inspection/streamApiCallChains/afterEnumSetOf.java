// "Replace EnumSet.of().stream() with Stream.of()" "true"

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;


public class Tests {
  interface HasCode {
    default String getCode() {return this.toString();}
  }

  enum Status implements HasCode {
    Approved, Pending, Stored
  }

  void test() {
    List<String> list = Stream.of(Status.Approved, (Status.Pending), Status.Stored)
      .map(HasCode::getCode).collect(toList());
  }
}
