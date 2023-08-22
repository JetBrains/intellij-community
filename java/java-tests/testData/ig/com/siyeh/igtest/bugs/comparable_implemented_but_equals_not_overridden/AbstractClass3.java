abstract class <warning descr="Class 'AbstractClass3' implements 'java.lang.Comparable' but does not override 'equals()'">AbstractClass3</warning> implements Comparable<AbstractClass3> {

  int field;

  public int compareTo(AbstractClass3 other) {
    return field > other.field ? 1 : (field == other.field ? 0 : -1);
  }

  public abstract boolean equals(Object other);
}