class List<T> {
    T t;

    private List() {
    }

    static <T> List<T> createList() {
        return new List<T>();
    }
}

class Test {
    void foo (){
        List x = List.createList();
        List[] y = new List [10];
    }
}