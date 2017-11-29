import java.util.Map;

class Test {

  public static void main(String[] args) {
    Map<Object, Object> b = <error descr="Incompatible types. Required Map<Object, Object> but 'newMapTrie' was inferred to T:
Incompatible equality constraint: Byte and Object">newMapTrie();</error>
    Map<Object, Map<Object, Object>> c = <error descr="Incompatible types. Required Map<Object, Map<Object, Object>> but 'newMapTrie' was inferred to T:
Incompatible equality constraint: Byte and Object">newMapTrie();</error>
    Map<Object, Map<Object, Map<Object, Object>>> d = <error descr="Incompatible types. Required Map<Object, Map<Object, Map<Object, Object>>> but 'newMapTrie' was inferred to T:
Incompatible equality constraint: Byte and Object">newMapTrie();</error>
    Map<Object, Map<Object, Map<Object, Map<Object, Object>>>> e = <error descr="Incompatible types. Required Map<Object, Map<Object, Map<Object, Map<Object, Object>>>> but 'newMapTrie' was inferred to T:
Incompatible equality constraint: Byte and Object">newMapTrie();</error>
  }

  public static <T extends Map<Byte, T>> T newMapTrie() {
    return null;
  }
}