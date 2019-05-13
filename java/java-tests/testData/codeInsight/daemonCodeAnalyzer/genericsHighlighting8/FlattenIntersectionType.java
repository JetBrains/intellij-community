interface BusinessEntity<E extends BusinessEntity<E>> {
}

interface EntityId<E extends BusinessEntity> {
    E getEntity();
}

class MyTest {
    <T extends BusinessEntity<T>> T getEntity(EntityId<T> defaultValue) {
        return getEntityID(defaultValue).getEntity();
    }

    public <P extends EntityId<?>> P getEntityID(P defaultValue) {
        return null;
    }
}
