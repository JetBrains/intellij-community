import java.util.Map;

class Test {

  public static void main(String[] args) {
    Map<Object, Object> b = <error descr="Incompatible types. Found: 'T', required: 'java.util.Map<java.lang.Object,java.lang.Object>'">newMapTrie</error>();
    Map<Object, Map<Object, Object>> c = <error descr="Incompatible types. Found: 'T', required: 'java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.lang.Object>>'">newMapTrie</error>();
    Map<Object, Map<Object, Map<Object, Object>>> d = <error descr="Incompatible types. Found: 'T', required: 'java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.lang.Object>>>'">newMapTrie</error>();
    Map<Object, Map<Object, Map<Object, Map<Object, Object>>>> e = <error descr="Incompatible types. Found: 'T', required: 'java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.util.Map<java.lang.Object,java.lang.Object>>>>'">newMapTrie</error>();
  }

  public static <T extends Map<Byte, T>> T newMapTrie() {
    return null;
  }
}