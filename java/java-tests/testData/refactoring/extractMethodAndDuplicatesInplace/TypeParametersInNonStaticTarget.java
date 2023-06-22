class Test<T> {

    static class Inner<I extends Integer, T>{
        public void get(List<T> list, I i) {
            <selection>list.get(i);</selection>
        }
    }
}