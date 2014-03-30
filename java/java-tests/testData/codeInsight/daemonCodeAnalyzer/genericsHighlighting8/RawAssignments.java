import java.io.*;
import java.util.*;

interface Foo {
    String getText(Map attributes) throws IOException;
    String getText(Properties attributes) throws IOException;
}

class Bar {
    void foo(Foo foo, Properties prop) throws IOException {
        foo.getText(prop);
    }
}
abstract class Hashtable<K,V> implements Map<K,V>, Cloneable {
}

abstract class Properties extends Hashtable<Object,Object> {
}
