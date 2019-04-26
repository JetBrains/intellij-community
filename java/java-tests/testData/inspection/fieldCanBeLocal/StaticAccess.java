class Test {
private static int count = 0;

	public <error descr="Invalid method declaration; return type required">FieldCanBeLocal</error>() {
		count = count + 1;
	}

}
