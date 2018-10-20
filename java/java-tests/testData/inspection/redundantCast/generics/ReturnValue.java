interface GenericValue<T> {
    T getValue();
}

interface GenericAttValue<T> extends GenericValue<T> {
}

interface Property {
    GenericAttValue<Object> getValue();
}

class RedCast {
    public GenericValue<String> getDataSourceName(Property property) {
        return  (GenericValue)  property.getValue();
    }
}
