import java.util.List;

class Test {

  public <E> EntityContainer<?, E, ?> findNestedContainer(Class<E> entityClass) {
    return null;
  }

  public <E2 > EntityContainer<?, E2, ?> readNestedEntity(Class<E2> entityClass) {
    return findNestedContainer(entityClass);
  }

  private interface EntityContainer<K extends List<E>, E, M extends Test & List<E>> {}
}