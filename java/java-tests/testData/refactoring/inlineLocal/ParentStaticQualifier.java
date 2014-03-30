import java.util.*;
class GrCaseLabel {
}

class A0 {
  protected static <T> List<T> findChildrenByType() {
    return null;
  }
}

class A extends A0 {
  void f() {
    final List<GrCaseLabel> labels = findChildrenByType();
    for (GrCaseLabel label : la<caret>bels) {
    }
  }
}
