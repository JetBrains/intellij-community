// "Replace with 'computeIfAbsent' method call" "GENERIC_ERROR_OR_WARNING"
import java.util.*;
class TestNewValue {
  static class EntityManagerFactoryProxy {}
  private final Map<String, EntityManagerFactoryProxy> proxiedFactories = new HashMap<>();
  public EntityManagerFactoryProxy getWrapperOriginal(String persistenceUnitName) {
      return proxiedFactories.computeIfAbsent(persistenceUnitName, n -> new EntityManagerFactoryProxy());
  }
}

