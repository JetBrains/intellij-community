package p;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    @Nullable
    public Object put(Object o, Object o2) {
        return null;
    }

    public Object remove(Object o) {
        return null;
    }

    public void putAll(@NotNull Map map) {

    }

    public void clear() {

    }

    @NotNull
    public Set keySet() {
        return null;
    }

    @NotNull
    public Collection values() {
        return null;
    }

    @NotNull
    public Set<Entry> entrySet() {
        return null;
    }
}