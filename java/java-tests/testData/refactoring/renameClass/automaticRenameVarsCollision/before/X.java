class XX { // rename me to Y and let the refactoring rename the variables too.
	void foo(XX x) {
		String y = "asdf";
		System.out.println("x: " + x + y);
	}
}
