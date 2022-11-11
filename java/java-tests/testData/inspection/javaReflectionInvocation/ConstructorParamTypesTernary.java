import java.lang.reflect.*;

public class ConstructorParamTypesTernary {
  public void generic(Class<?> c, Method method) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
    final Constructor<?> constructor = c.getConstructor(Type.class);
    constructor.newInstance(
      method.getGenericReturnType() instanceof ParameterizedType
      ? method.getGenericReturnType()
      : method.getReturnType()
    );
  }
}