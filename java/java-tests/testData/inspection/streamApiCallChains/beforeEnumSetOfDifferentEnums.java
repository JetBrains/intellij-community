// "Fix all 'Stream API call chain can be simplified' problems in file" "false"

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

  enum Status2 implements HasCode {
    Finished
  }

  void test() {
    List<String> list = EnumSet.of(Status.Approved, (Status.Pending), Status.Stored, Status2.Finished).st<caret>ream()
      .map(HasCode::getCode).collect(toList());
  }
}
