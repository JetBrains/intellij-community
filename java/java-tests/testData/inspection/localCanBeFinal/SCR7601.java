class TestFinal2 {
    static void foo(final boolean b) {
	if (b) {
	    int <warning descr="Variable 'i' can have 'final' modifier">i</warning> = 12;
	    System.out.println("i = " + i);
	}
    }
}
