package renameCollisions;

class LocalClass {
	public static final int SN_STATIC_FIELD = 2;
	public static void snStaticMethod() {
	}
}

public class RenameCollisions {
	class Inner<caret>Class {
		public static final int SN_STATIC_FIELD = 14;
		public void snMethod() {
		}
	}

	public void instanceContext() {
		LocalClass localClass = new LocalClass();
		int var1 = LocalClass.SN_STATIC_FIELD;
		LocalClass.snStaticMethod();

		InnerClass innerClass = new InnerClass();
		int var4 = InnerClass.SN_STATIC_FIELD;
	}

	public static void staticContext() {
		InnerClass innerClass = new RenameCollisions().new InnerClass();
		int var3 = InnerClass.SN_STATIC_FIELD;
	}
}
