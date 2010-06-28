// "Use existing implementation of 'm'" "true"
interface I<T> {
    void <caret>m(T param);
}

class A implements I<Object> {
    public void m(Object param) {
        System.out.println(param.toString());
    }
}

class B implements I<String> {
}

class C extends B {
}