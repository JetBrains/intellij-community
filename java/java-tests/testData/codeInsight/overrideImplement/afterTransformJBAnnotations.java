package p;
import java.util.Collection;
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
        return null;
    }

    @NN
    public Collection values() {
        return null;
    }

    @NN
    public Set<Entry> entrySet() {
        return null;
    }
}

@interface N {}
@interface NN {}