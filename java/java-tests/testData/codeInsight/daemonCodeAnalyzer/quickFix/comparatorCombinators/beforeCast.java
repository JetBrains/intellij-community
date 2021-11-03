// "Replace with 'Comparator.comparing'" "true"

import java.util.Comparator;

class CodeSample {
  public void foo() {
    final Comparator CMP = (Comparator<Entity>) (o1,<caret> o2) -> o1.getUuid()
      .compareTo(o2.getUuid());
  }

  private class Entity {
    public <T> Comparable getUuid() {
      return null;
    }
  }
}