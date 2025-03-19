// "Convert to record class" "true-preview"
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

class Outer {
  private static class Parameterized<caret>TypeImpl implements ParameterizedType {
    private final Type rawType;

    private final Type[] typeArguments;

    public ParameterizedTypeImpl(Type rawType, Type... typeArguments) {
      this.rawType = rawType;
      this.typeArguments = typeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return typeArguments;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type getOwnerType() {
      return null;
    }
  }
}