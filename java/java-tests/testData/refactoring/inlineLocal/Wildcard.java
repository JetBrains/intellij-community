import java.util.*;
class Test {
  void foo() {
    final Collection<? extends Number> extensions = getExtensions();
    for (Number extension : exte<caret>nsions) {
    }
  }

  Collection<? extends Number> getExtensions() {return null;}
}

