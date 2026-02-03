class C<T> {
    void put(T t) {
        System.out.println(t);
    }
}

class CString extends C<String> {
    void put(String t) {
        System.out.println(t +"Text");
    }
}