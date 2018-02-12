import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class Bugs {
  static class EnumMethod {
    public static <T extends Enum<T>> boolean isEnum(final Class<T> enumClass, final String candidate) {
      try {
        final Method method = enumClass.getMethod("valueOf", String.class);
        Object o = method.invoke(null, candidate);
        System.out.println(o.getClass() + "." + o);
        return true;
      }
      catch (final IllegalArgumentException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        return false;
      }
    }
  }

  static class ConstructorInSubclass {
    static abstract class X {
    }

    public static class Y extends X {
      public Y(int i) {
        System.out.println("ok");
      }
    }

    static X test(Class<? extends X> clazz) throws Exception {
      return clazz.getConstructor(int.class)
        .newInstance(1);
    }

    public static void main(String[] args) throws Exception {
      test(Y.class);
    }
  }
}