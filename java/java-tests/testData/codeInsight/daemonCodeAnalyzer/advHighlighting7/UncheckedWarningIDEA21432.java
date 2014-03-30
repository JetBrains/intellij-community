import java.util.Map;

class MissingWarning {

  static final Map<String, String> VALID_VALUES_BY_KEY = <warning descr="Unchecked generics array creation for varargs parameter">newExceptionOnNullHashMap</warning>(
    "Invalid key",
    newEntry("key1", "valA"), // <<--- compiler warning on this line
    newEntry("key2", "valB"),
    newEntry("key3", "valC"),
    newEntry("key4", "valD")
  );

  public static <K, V> Map<K, V> newExceptionOnNullHashMap(final String <warning descr="Parameter 'exceptionMessage' is never used">exceptionMessage</warning>,
                                                           Map.Entry<K, V>... <warning descr="Parameter 'entries' is never used">entries</warning>)
  {
    return null;
  }

  public static <K, V> Map.Entry<K, V> newEntry(K <warning descr="Parameter 'key' is never used">key</warning>, V <warning descr="Parameter 'value' is never used">value</warning>) {
    return null;
  }
}