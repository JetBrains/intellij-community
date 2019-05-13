class C1<T extends C1<T>>{
  public static class C2<K extends C1<K>> extends C1<K>{}
  public static class C3<Z> extends C2<<error descr="No wildcard expected">? extends C3<Z></error>>{}
}