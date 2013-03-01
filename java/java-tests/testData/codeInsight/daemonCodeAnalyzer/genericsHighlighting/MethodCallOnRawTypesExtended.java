import java.util.Collections;
import java.util.List;

class Bar {
    private List<Property> getChildren(Property property, PropertiesContainer c) {
         return property.getChildren(c);
    }

    static class PropertiesContainer<M extends Property> {}

}

class Property<K> {
    public List<? extends Property<K>> getChildren(K container) {
        return Collections.emptyList();
    }
}