class ContainerUtil {
    interface Condition<T> {
        boolean value(T object);
    }

    public static <T> T find(Iterable<? extends T> iterable, final T equalTo) {
        return <ref>find(iterable, new Condition<T>() {
            public boolean value(final T object) {
                return equalTo == object || equalTo.equals(object);
            }
        });
    }

    public static <T> T find(Iterable<? extends T> iterable, Condition<T> condition) {
        return null;
    }

}
