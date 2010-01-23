class Y { // rename me to Y and let the refactoring rename the variables too.
	void foo(Y y) {
		String yz = "asdf";
		System.out.println("x: " + y + yz);
	}
}
