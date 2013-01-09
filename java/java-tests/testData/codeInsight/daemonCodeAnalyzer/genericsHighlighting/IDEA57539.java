class E<T> {
    Class<T[]> o = <error descr="Cannot select from a type variable">T[]</error>.class;
}

class MyClass<T> {
    Class<T[]> getTs() {
        return <error descr="Cannot select from a type variable">T[]</error>.class;
    }

    public static void main(String[] args) {
        new MyClass<String>().getTs();
    }
}