class Types {
	public long <caret>primitiveMethod(boolean b) {
		return b ? 1 : 0;
	}
	public void primitiveContext(boolean b) {
		int i = !b ? 1 : 0;
		long l = b ? 1 : 0;
	}
}
