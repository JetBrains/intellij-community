
class Test{
    interface Map<K, V>{
        K put(K k, V v);
    }

    class HashMap implements Map<Integer, Integer>{
    }

    {
        HashMap map = new HashMap();
        map.put(new Integer(1), new Integer(1)).<ref>byteValue();
    }
}