import java.util.*;

interface TypesafeMap<BASE> {

  @SuppressWarnings({"UnusedDeclaration"})
  public interface Key<BASE,VALUE> { }

  public <VALUE, KEY extends Key<BASE,VALUE>>
    boolean has(Class<KEY> key);

  public <VALUE, KEY extends Key<BASE,VALUE>>
    VALUE get(Class<KEY> key);

  public <VALUEBASE, VALUE extends VALUEBASE, KEY extends Key<BASE,VALUEBASE>>
    VALUE set(Class<KEY> key, VALUE value);

  public <VALUE, KEY extends Key<BASE,VALUE>>
    VALUE remove(Class<KEY> key);

  public Set<Class<?>> keySet();

  public <VALUE, KEY extends Key<CoreMap, VALUE>>
    boolean containsKey(Class<KEY> key);
}


interface CoreMap extends TypesafeMap<CoreMap> { }

interface CoreAnnotation<V>
  extends TypesafeMap.Key<CoreMap, V> {

  public Class<V> getType();
}


class CoreMaps {

  public static <K,V> Map<K,V> toMap(Collection<CoreMap> coremaps,
      Class<CoreAnnotation<K>> keyKey, Class<CoreAnnotation<V>> valueKey) {

    Map<K,V> map = new HashMap<K,V>();
    for (CoreMap cm : coremaps) {
      map.put(cm.get(keyKey), cm.get(valueKey));
    }

    return map;
  }
}

