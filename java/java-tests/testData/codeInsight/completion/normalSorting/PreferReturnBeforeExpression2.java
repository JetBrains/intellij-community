class Test {
    public static List<String> test() {
        ret<caret> wrap(foo(), foo());
    }

    private static List<String> wrap(List<String> val1, List<String> val2) {
        return val1;
    }

    public static <T> boolean retainAll(Collection<T> collection, Condition<? super T> condition) {
        return false;
    }
}