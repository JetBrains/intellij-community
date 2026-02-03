class C<T> {
    void put<caret>(Object o) {
        System.out.println(o);
    }
}

class CString extends C<String> {
    void put(Object o) {
        System.out.println(o+"Text");
    }
}