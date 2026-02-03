public class Util {

  public static <T,V> List<V> map(@NotNull Function<T, V> mapping) { }

  public Object[] getVariants() {
    return map(new Function<Object, ArrayIndexOutOfBoundsException<caret>>() { }
  }


}
interface Function<Param, Result> {
  Result fun(Param param);
}
