class Test {
    public final Class<?> aClass = Class.forName(Test.class.getName);

    void foo() {
      Class clazz = aClass;
    }
}