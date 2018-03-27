import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class X {
  public void foo(Class<?> cls)
  {
    Stream.of(cls.getMethods())
      .filter(method ->//my comment to keep
                Collection.class.isAssignableFrom(<selection>method.getReturnType()</selection>) || Map.class.isAssignableFrom(method.getReturnType()))
      .collect(Collectors.<Method>toList());
  }
}
