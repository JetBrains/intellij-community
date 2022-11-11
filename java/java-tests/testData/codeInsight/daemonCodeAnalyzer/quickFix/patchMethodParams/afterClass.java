// "Replace 'Double.class' with 'Integer.class'" "true-preview"
class Demo {
  native static <T> T tryCast(Object obj, Class<T> clazz);

  void test(Object obj) {
    Integer i = tryCast(obj, Integer.class);
  }
}
