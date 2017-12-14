

import java.lang.reflect.Field;

class Inner {
    public static void main(String[] args) {
      final Field[] declaredFields = Inner.class.getDeclaredFields();
      Field f = declaredFields[0];

      if (f.get<caret>Type().isAssignableFrom(Integer.class)) {
        //
      } else if (f.getType().isAssignableFrom(Integer.class)) {
        //
      } else if (f.getType().isAssignableFrom(Integer.class)) {
        //
      }
    }
}
