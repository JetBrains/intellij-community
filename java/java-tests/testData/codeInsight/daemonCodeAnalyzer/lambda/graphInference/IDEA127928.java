import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class CastLambdaParameter {

  void main(final Map<Integer, String> map) {
    Set<Function<Object, String>> property2formatter = foo(bar((joint) ->  map.get((Integer)joint)));
  }

  public static <B> Set<B> foo(List<B> property2name) {return property2name != null ? null : null;}
  public static <B> Set<B> foo(List<B>... property2name) {return property2name != null ? null : null;}
  public static <B> Set<B> foo(Set<B> property2name) {return property2name != null ? null : null;}

  public static <TB> List<TB> bar(TB b) {
    return null;
  }

}

