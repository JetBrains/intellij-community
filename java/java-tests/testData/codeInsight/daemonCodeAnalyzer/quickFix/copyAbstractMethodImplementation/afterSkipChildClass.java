// "Use existing implementation of 'm'" "true"
interface I<T> {
    void m(T param);
}

class A implements I<Object> {
    public void m(Object param) {
        System.out.println(param.toString());
    }
}

class B implements I<String> {
    public void m(String param) {
        <selection>System.out.println(param.toString());</selection>
    }
}

class C extends B {
}