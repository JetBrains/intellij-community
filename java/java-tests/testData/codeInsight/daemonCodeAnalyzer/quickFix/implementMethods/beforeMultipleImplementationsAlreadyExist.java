// "Implement method 'foo'" "true"

abstract class A<T> {
    abstract String f<caret>oo(T t);
}

class B extends A<String> {

    @Override
    String foo(String s1) {
        return null;
    }
}

class ABC extends A<Integer> {}