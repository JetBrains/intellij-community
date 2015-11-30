import java.util.Map;

class Test {

  public static void main(String[] args) {
    Map<Object, Object> b = newMapTrie<error descr="'newMapTrie()' in 'Test' cannot be applied to '()'">()</error>;
    Map<Object, Map<Object, Object>> c = newMapTrie<error descr="'newMapTrie()' in 'Test' cannot be applied to '()'">()</error>;
    Map<Object, Map<Object, Map<Object, Object>>> d = newMapTrie<error descr="'newMapTrie()' in 'Test' cannot be applied to '()'">()</error>;
    Map<Object, Map<Object, Map<Object, Map<Object, Object>>>> e = newMapTrie<error descr="'newMapTrie()' in 'Test' cannot be applied to '()'">()</error>;
  }

  public static <T extends Map<Byte, T>> T newMapTrie() {
    return null;
  }
}