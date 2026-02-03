import java.util.*;

class X {
class MyMap implements Map {
    public int size() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isEmpty() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean containsKey(Object key) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean containsValue(Object value) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Object get(Object <flown1111>key) {
        return key.<flown11111>getClass();
    }

    public Object put(Object key, Object value) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Object remove(Object key) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void putAll(Map m) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void clear() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Set keySet() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Collection values() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Set entrySet() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}

public String getEncoding( int <caret>virtualFile, boolean useParentDefaults) {
    Map<Integer,String> myMapping = null;
  int <flown11>parent = <flown1>virtualFile;
  while (true) {
    String charset = myMapping.get(<flown111>parent);
    if (charset != null || !useParentDefaults) return charset;
    if (parent == 0) break;
    parent = parent-1;
  }
  return null;
}

}