class Test {
  void constructors(Class<?> clazz) {
		System.out.println(Arrays.toString(clazz.getDeclared<caret>Constructors()));
	}
}