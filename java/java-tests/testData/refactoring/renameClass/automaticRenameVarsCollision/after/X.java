class Y { // rename me to Y and let the refactoring rename the variables too.
	void foo(Y x) {
		String y = "asdf";
		System.out.println("x: " + x + y);
	}
}
