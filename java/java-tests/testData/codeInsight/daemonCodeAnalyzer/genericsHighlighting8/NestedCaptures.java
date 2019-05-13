
import java.util.Collection;

class Test {
  public void entitySetChanged(EntitySetEvent<? extends Number> event) {
    event.getUpdatedEntities().forEach(entity -> {
      entity.intValue();
    });
  }
}

interface EntitySetEvent<V> {
  Collection<? extends V> getUpdatedEntities();
}