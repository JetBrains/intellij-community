
class Test {

    private static Class<? extends Object[]> test(Class<?> arrayType) {
        return arrayType.asSubclass(Object[].class);
    }

}
