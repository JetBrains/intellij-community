interface I<K> {
}

abstract class SpringHighlightingTestCase<T extends I>{
  @SuppressWarnings("unchecked")
  protected Class<T> getBuilderClass() {
    return (Class<T>)I.class;
  }
}
