class Foo {

    {
        Object o = getUD<caret>
    }

    final String getUserDataString() {}
    final <T> T getUserData(T t) {}
}