package foo;

class Test {
    public static void main(final String[] args) {
	int <warning descr="Variable 'foo' can have 'final' modifier">foo</warning> = 12;
    }
}