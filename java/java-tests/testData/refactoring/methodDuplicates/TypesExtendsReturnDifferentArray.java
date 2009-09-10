class Types {
	public static Object[] <caret>arrayMethod(String s) {
		return new String[] {s};
	}
	public static void arrayContext(String s) {
		String[] sa = new String[] {s.substring(0)};
		Object[] oa = new String[] {s};
	}
}
