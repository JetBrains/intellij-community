class <warning descr="Class 'CoolClass' implements 'java.lang.Comparable' but does not override 'equals()'"><caret>CoolClass</warning> implements Comparable<CoolClass> {

  public int compareTo(CoolClass other) {
    return 0;
  }
}