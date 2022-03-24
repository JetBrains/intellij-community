// "Replace with 'Comparator.comparing'" "true"

import java.util.Comparator;

class CodeSample {
  public void foo() {
    final Comparator CMP = (Comparator<Entity>) Comparator.comparing((Entity o) -> o.getUuid());
  }

  private class Entity {
    public <T> Comparable getUuid() {
      return null;
    }
  }
}