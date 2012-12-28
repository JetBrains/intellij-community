public class C {
  private int x;

  public boolean equals(Object object) {
    if (object instanceof C) {
      if (((C)object).x == x) {
        return true;
      }
    }
    return false;
  }
}