class RedundantCast {
  ParentStrategy strategy;

  public void cast() {
    use((ChildStrategy) strategy);
  }

  private <T> void use(ChildStrategy<T> strategy) {
  }
}

interface ParentStrategy<T> {
}

interface ChildStrategy<T> extends ParentStrategy<T> {
}