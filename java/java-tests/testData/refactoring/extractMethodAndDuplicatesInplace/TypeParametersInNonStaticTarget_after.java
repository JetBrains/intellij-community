class Test<T> {

    static class Inner<I extends Integer, T>{
        public void get(List<T> list, I i) {
            extracted(list, i);
        }

        private static <I extends Integer> void extracted(List<T> list, I i) {
            list.get(i);
        }
    }
}