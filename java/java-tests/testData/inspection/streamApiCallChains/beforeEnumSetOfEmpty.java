// "Fix all 'Simplify stream API call chains' problems in file" "false"

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
    List<String> list = EnumSet.of().st<caret>ream()
      .map(HasCode::getCode).collect(toList());
  }
}
