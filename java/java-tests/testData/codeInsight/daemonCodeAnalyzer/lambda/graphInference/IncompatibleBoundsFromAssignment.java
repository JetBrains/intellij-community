import java.util.Map;

class Test {

  public static void main(String[] args) {
    <error descr="Incompatible types. Found: 'T', required: 'java.util.Map<java.lang.Object,java.lang.Object>'">Map<Object, Object> b = newMapTrie();</error>
    <error descr="Incompatible types. Found: 'T', required: 'java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.lang.Object>>'">Map<Object, Map<Object, Object>> c = newMapTrie();</error>
    <error descr="Incompatible types. Found: 'T', required: 'java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.lang.Object>>>'">Map<Object, Map<Object, Map<Object, Object>>> d = newMapTrie();</error>
    <error descr="Incompatible types. Found: 'T', required: 'java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.lang.Object>>>>'">Map<Object, Map<Object, Map<Object, Map<Object, Object>>>> e = newMapTrie();</error>
  }

  public static <T extends Map<Byte, T>> T newMapTrie() {
    return null;
  }
}