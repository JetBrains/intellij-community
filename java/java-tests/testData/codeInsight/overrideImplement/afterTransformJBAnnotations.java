package p;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

class M implements Map {
    public int size() {
        return 0;
    }

    public boolean isEmpty() {
        return false;
    }

    public boolean containsKey(Object o) {
        return false;
    }

    public boolean containsValue(Object o) {
        return false;
    }

    public Object get(Object o) {
        return null;
    }

    @N
    public Object put(Object o, Object o2) {
        return null;
    }

    public Object remove(Object o) {
        return null;
    }

    public void putAll(@NN Map map) {

    }

    public void clear() {

    }

    @NN
    public Set keySet() {
        return Collections.emptySet();
    }

    @NN
    public Collection values() {
        return Collections.emptyList();
    }

    @NN
    public Set<Entry> entrySet() {
        return Collections.emptySet();
    }
}

@interface N {}
@interface NN {}