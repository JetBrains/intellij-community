class MyTest {
    interface Container<K, V> {
        V read(K k);
    }

    interface Container2<K, V> extends Container<K, V> {
        V read(K k);
    }

    interface Container3<K, V> extends Container2<K, V> {
        V read(K k);
    }

    void test(Container3<String, String> c3) {
        Container<String, String> c = c3;
        c.read()
    }
}