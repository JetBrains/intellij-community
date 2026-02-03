class List <T> {}

class A<T> {
    public List<? extends T> method1() {
        return null;
    }
    public T method2(T t) {
        return null;
    }
}