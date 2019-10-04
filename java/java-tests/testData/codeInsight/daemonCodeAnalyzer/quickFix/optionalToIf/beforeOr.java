// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  String reusesVariable(String in) {
    return Optional.of<caret>(in).map(s -> toName(s)).or(() -> Optional.of(toDefaultName())).get();
  }

  String removesRedundantAssignment(String in) {
    return Optional.of(in).or(() -> Optional.of(toDefaultName())).or(() -> Optional.of(toDefaultName())).get();
  }

  private String toName(String str) {
    if (str.startsWith("name")) return str.substring(4);
    return null;
  }

  private String toDefaultName() {
    return "defaultName";
  }

}