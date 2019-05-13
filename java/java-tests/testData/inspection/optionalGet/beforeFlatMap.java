// "Fix all 'Optional.get() is called without isPresent() check' problems in file" "true"
import java.util.Optional;

class Test {
  class PropertyHolder<T> {
    T property;

    Optional<T> getProperty() {
      return Optional.ofNullable(property);
    }

    Optional<T> getProperty(String s) {
      return Optional.ofNullable(property);
    }
    Optional<T> getPropertyEx() throws Exception {
      return Optional.ofNullable(property);
    }
  }

  class Smth<T> {
    PropertyHolder<T> propertyHolder;

    Optional<PropertyHolder<T>> getPropertyHolder() {
      return Optional.ofNullable(propertyHolder);
    }

    Optional<PropertyHolder<T>> getPropertyHolderEx() throws Exception {
      return Optional.ofNullable(propertyHolder);
    }
  }

  void test() throws Exception {
    Optional<Long> property = new Smth<Long>().getPropertyHolder().ge<caret>t().getProperty();
    Optional<Long> property1 = new Smth<Long>().getPropertyHolderEx().get().getProperty("foo");
    Optional<Long> property2 = new Smth<Long>().getPropertyHolderEx().get().getPropertyEx();
  }
}