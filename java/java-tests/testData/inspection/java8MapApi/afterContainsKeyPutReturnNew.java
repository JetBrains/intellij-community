import java.util.*;
class TestNewValue {
  static class EntityManagerFactoryProxy {}
  private final Map<String, EntityManagerFactoryProxy> proxiedFactories = new HashMap<>();
  public EntityManagerFactoryProxy getWrapperOriginal(String persistenceUnitName) {
    return proxiedFactories.computeIfAbsent(persistenceUnitName, k -> new EntityManagerFactoryProxy());
  }
}

