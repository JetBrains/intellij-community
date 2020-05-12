import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class Example {
  @FunctionalInterface
  public interface ObjectWriter<T> {
    void writeObject(Writer writer, T object);
  }

  protected abstract ObjectWriter<?> getWriter(Type type);

  protected abstract <T> void writeList(Writer out, List<T> list, ObjectWriter<T> valueWriter);

  protected abstract <T> void writeSet(Writer out, Set<T> set, ObjectWriter<T> valueWriter);

  protected abstract <K, V> void writeMap(Writer out, Map<K, V> map, ObjectWriter<K> keyWriter, ObjectWriter<V> valueWriter);

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected ObjectWriter<?> getCollectionWriter(ParameterizedType parameterizedType) {
    Type rawType = parameterizedType.getRawType();
    if (rawType instanceof Class) {
      Class clazz = (Class)rawType;
      if (List.class.isAssignableFrom(clazz)) {
        Type elementType = parameterizedType.getActualTypeArguments()[0];
        ObjectWriter elementWriter = getWriter(elementType);
        //(List) is marked redundant
        return (writer, object) -> writeList(writer, (List)object, elementWriter);
      } else if (Set.class.isAssignableFrom(clazz)) {
        Type elementType = parameterizedType.getActualTypeArguments()[0];
        ObjectWriter elementWriter = getWriter(elementType);
        //(Set) is marked redundant
        return (writer, object) -> writeSet(writer, (Set)object, elementWriter);
      } else if (Map.class.isAssignableFrom(clazz)) {
        Type keyType = parameterizedType.getActualTypeArguments()[0];
        ObjectWriter keyWriter = getWriter(keyType);
        Type valueType = parameterizedType.getActualTypeArguments()[1];
        ObjectWriter valueWriter = getWriter(valueType);
        //(Map) is marked redundant
        return (writer, object) -> writeMap(writer, (Map)object, keyWriter, valueWriter);
      } else {
        throw new RuntimeException("Unable to create writer for generic type: " + clazz);
      }
    } else {
      throw new RuntimeException("Unable to create writer for raw type: " + rawType);
    }
  }
}

abstract class SimplifiedExample {
  @FunctionalInterface
  public interface ObjectWriter<T> {
    void writeObject(T object);
  }

  protected abstract <T> void writeList(List<T> list);

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected ObjectWriter<?> getCollectionWriter() {
    return (Object object) -> writeList((List)object);
  }
}
