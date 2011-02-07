class Key<T> { T t; }

class WKey<W, T> extends Key<T> { W w; }

class Items {
    <T> void addItem(Key<T> key, T value) {}

    <T, W> void addItem(WKey<W, T> key, T value) {}
}

class IBug {
    public IBug() {
        Items items = new Items();

        WKey<Object, String> sk = new WKey<Object, String>();

        items.<ref>addItem(sk,  "");
    }
}