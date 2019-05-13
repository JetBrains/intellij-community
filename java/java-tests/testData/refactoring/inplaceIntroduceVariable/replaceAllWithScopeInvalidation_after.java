

import java.lang.reflect.Field;

class Inner {
    public static void main(String[] args) {
      final Field[] declaredFields = Inner.class.getDeclaredFields();
      Field f = declaredFields[0];

        Class<?> newType = f.getType();
        if (newType.isAssignableFrom(Integer.class)) {
        //
      } else if (newType.isAssignableFrom(Integer.class)) {
        //
      } else if (newType.isAssignableFrom(Integer.class)) {
        //
      }
    }
}
