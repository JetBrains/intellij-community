// "Convert to record class" "true-preview"
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

class Outer {
    private record ParameterizedTypeImpl(Type rawType, Type... typeArguments) implements ParameterizedType {

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