class <caret>LocalClass {
	public static final int STATIC_FIELD = 5;
	public static void staticMethod() {
	}
	int myField;
	public void method() {
	}
}

public class RenameCollisions {
	private String myDeclarationUsage;
	public String declarationUsage(String s) {
		return s + myDeclarationUsage;
	}

	public static void staticContext() {
		String.CASE_INSENSITIVE_ORDER.getClass();
		String.valueOf(0);
	}
}
