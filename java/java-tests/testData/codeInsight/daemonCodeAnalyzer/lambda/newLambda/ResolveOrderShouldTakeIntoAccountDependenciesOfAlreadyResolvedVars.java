import java.util.Map;

interface RawEntity<T> {}
interface AoContributor extends RawEntity<Integer> {}
class Test {
  void foo(Map<String, AoContributor> contributors) {
    AoContributor c = contributors.computeIfAbsent("",  email -> create(AoContributor.class, of("clmn", email)));
  }

  static <T extends RawEntity<K>, K> T create(Class<T> c, Map<String, Object> v2) {
    return null;
  }

  static <K1, V1> Map<K1, V1> of(K1 k1, V1 v1) {
    return null;
  }
}