abstract class TypeToken<T> {
    private <T> TypeToken<T> tt(Class<T> t) { return null; }
    private <T> void checkedTestInexactSupertype(TypeToken<T> expectedSuperclass, TypeToken<? extends T> type) {}
    TypeToken<? super Integer> ft = null;

    {
        checkedTestInexactSupertype(ft, tt(Integer.class));
    }
}