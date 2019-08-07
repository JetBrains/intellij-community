// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  String checkForNullable(String in) {
    return Optional.<caret>of(in).map(s -> getStrOrNull(s)).get();
  }

  String checkIsRemovedForNotNull(String in) {
    return Optional.of(in).map(s -> id(s)).filter(s -> s.length() > 2).get();
  }

  String twoMapsProduceTwoVariables(String in, boolean b) {
    return Optional.of(in).map(s -> id(s)).map(s -> getStrIfTrue(s, b)).filter(s -> s.length > 2).get();
  }

  private String id(String s) {
    return s;
  }

  private String getStrOrNull(String s) {
    return s.length() > 2 ? s : null;
  }

  private String getStrIfTrue(String s, boolean b) {
    return b ? s : null;
  }

}