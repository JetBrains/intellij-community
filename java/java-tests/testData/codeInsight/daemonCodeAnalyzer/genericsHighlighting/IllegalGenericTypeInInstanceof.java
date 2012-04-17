class A<T> {
  public void foo(Object object) {
    if (object instanceof <error descr="Illegal generic type for instanceof">B</error>) {}
    if (object instanceof A.B) {}
    if (object instanceof A<?>.B) {}
    if (object instanceof A<?>) {}
    if (object instanceof <error descr="Illegal generic type for instanceof">A<String></error>) {}
    if (object instanceof A) {}
    if (object instanceof A[]) {}
    if (object instanceof <error descr="Illegal generic type for instanceof">B[]</error>) {}
    if (object instanceof A.B[]) {}
  }

  private class B {
  }
}

class A1 {
  public void foo(Object object) {
    if (object instanceof B1) {}
    if (object instanceof A1.B1) {}
    if (object instanceof B1[]) {}
  }

  private class B1 {
  }
}