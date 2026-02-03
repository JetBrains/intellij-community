class Test<U> {
    public Optional<U> bar(boolean empty, U state) {
        Optional<U> o = empty ? Optional.empty() : Optional.of(state);
        return empty ? Optional.empty() : Optional.empty();
    }

    static class Optional<T> {
        public static <U> Optional<U> empty() {
            return null;
        }

        public static <U> Optional<U> of(U state) {
            return null;
        }
    }
}