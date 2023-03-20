class E<T> {
    Class<T[]> o = <error descr="Cannot access class object of a type parameter">T[]</error>.class;
}

class MyClass<T> {
    Class<T[]> getTs() {
        return <error descr="Cannot access class object of a type parameter">T[]</error>.class;
    }

    public static void main(String[] args) {
        new MyClass<String>().getTs();
    }
}