class GenericTypeMismatch {
    interface Key<T> {}

    static final Key<String> KEY = new Key<String>() {};

    <T> T getByKey(Key<T> key) {
        return null;
    }

    void test() {
        if(ge<caret>tByKey(KEY))
        {

        }
    }
}
