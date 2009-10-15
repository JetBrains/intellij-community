class List<T> {
  void addAll(List<? extends T> l);
}

class Key<T> {}

class FooBase {
    Key<List<String>> STRING_LIST;

    <T> T get(Key<T> key);

    {
        List<String> l;
        l.addAll(get(STRING_LIST)<caret>)
    }


}