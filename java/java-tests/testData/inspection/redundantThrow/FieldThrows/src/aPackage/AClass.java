package aPackage;

class Test {
	private final Test2 test2 = new Test2();

	public Test() throws Exception {
	}
}

class Test2 {
	public Test2() throws Exception {
		throw new Exception();
	}
}