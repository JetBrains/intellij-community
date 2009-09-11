package renameCollisions;

class <caret>LocalClass {
	public static final int SN_STATIC_FIELD = 2;
	public static void snStaticMethod() {
	}
}

public class RenameCollisions {
	public static class StaticInnerClass {
		public static final int SN_STATIC_FIELD = 10;
		public static void snStaticMethod() {
		}
	}

	public void instanceContext() {
		LocalClass localClass = new LocalClass();
		int var1 = LocalClass.SN_STATIC_FIELD;
		LocalClass.snStaticMethod();

		StaticInnerClass staticInnerClass = new StaticInnerClass();
		int var3 = StaticInnerClass.SN_STATIC_FIELD;
		StaticInnerClass.snStaticMethod();
	}

	public static void staticContext() {
		StaticInnerClass staticInnerClass = new StaticInnerClass();
		int var2 = StaticInnerClass.SN_STATIC_FIELD;
		StaticInnerClass.snStaticMethod();
	}
}
