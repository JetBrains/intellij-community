import java.lang.reflect.InvocationTargetException;

class Low {

  void g(Class<?> x) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
      x.getConstructor().newInstance();
  }
}