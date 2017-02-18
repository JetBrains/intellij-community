class NotUsedTest {
    public static void main(String[] args) {
	boolean unused = true;
	<warning descr="The value true assigned to 'unused' is never used">unused</warning> = true;
	<warning descr="The value true assigned to 'unused' is never used">unused</warning> = true;
    }
}
