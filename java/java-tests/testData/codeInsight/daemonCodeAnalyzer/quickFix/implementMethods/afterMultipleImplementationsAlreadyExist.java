// "Implement method 'foo'" "true"

abstract class A<T> {
    abstract String foo(T t);
}

class B extends A<String> {

    @Override
    String foo(String s1) {
        return null;
    }
}

class ABC extends A<Integer> {
    @Override
    String foo(Integer integer) {
        return null;
    }
}