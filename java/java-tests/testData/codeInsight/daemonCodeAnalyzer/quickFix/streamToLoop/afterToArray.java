// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public class Main {
  private static Object[] test(int[] numbers) {
      List<Integer> list = new ArrayList<>();
      for (int number: numbers) {
          Integer integer = number;
          list.add(integer);
      }
      return list.toArray();
  }

  private static Integer[][] test2d(int[] numbers) {
      List<Integer[]> list = new ArrayList<>();
      for (int number: numbers) {
          Integer n = number;
          Integer[] integers = new Integer[]{n};
          list.add(integers);
      }
      return list.toArray(new Integer[0][]);
  }

  private static Number[] testCovariant(int[] numbers) {
      List<Integer> list = new ArrayList<>();
      for (int number: numbers) {
          Integer integer = number;
          list.add(integer);
      }
      return list.toArray(new Integer[0]);
  }

  private static Number[] testCovariantLambda(int[] numbers) {
      List<Integer> list = new ArrayList<>();
      for (int number: numbers) {
          Integer integer = number;
          list.add(integer);
      }
      return list.toArray(new Integer[0]);
  }

  private static <A> A[] toArraySkippingNulls(List<?> list, IntFunction<A[]> generator) {
      List<Object> result = new ArrayList<>();
      for (Object o: list) {
          if (o != null) {
              result.add(o);
          }
      }
      return result.toArray(generator.apply(0));
  }

  private static List<?>[] testGeneric(int[] numbers) {
      List<List<Integer>> list = new ArrayList<>();
      for (int number: numbers) {
          Integer n = number;
          List<Integer> integers = Collections.singletonList(n);
          list.add(integers);
      }
      return list.toArray(new List<?>[0]);
  }

  private static Number[] testTypeMismatch(Object[] objects) {
      List<Object> list = new ArrayList<>();
      for (Object object: objects) {
          if (object instanceof Number) {
              list.add(object);
          }
      }
      return list.toArray(new Number[0]);
  }

  public static class RawTypeProblem {
    private static Class<?>[] resolver(String list) {
        // convert to loop
        List<Class<?>> result = new ArrayList<>();
        for (String s: list.split(",")) {
            Class<?> aClass = loadType(s);
            result.add(aClass);
        }
        return result.toArray(new Class[0]);
    }

    private static Class<?> loadType(String typeName) {
      return null;
    }
  }

  public static void main(String[] args) {
    System.out.println(Arrays.asList(test(new int[] {1,2,3})));
    System.out.println(Arrays.asList(test2d(new int[] {1,2,3})));
    System.out.println(Arrays.asList(testCovariant(new int[] {1,2,3})));
    System.out.println(Arrays.asList(testCovariantLambda(new int[] {1,2,3})));
    System.out.println(Arrays.asList(testGeneric(new int[] {1,2,3})));
    System.out.println(Arrays.asList(testTypeMismatch(new Object[]{1, 2, 3, "string", 4})));
  }
}