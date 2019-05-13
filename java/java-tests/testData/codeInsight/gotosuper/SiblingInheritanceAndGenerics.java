package x;

class Data {
}

abstract class Abstract<T extends Data> {
    public String <caret>foo(T data) {
        return "foo";
    }
}

interface Interface<T extends Data> {
    public String foo(T data);
}

class Implementation extends Abstract<Data> implements Interface<Data> {

}
