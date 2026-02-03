public class RenameCollisions {
	public static final int STATIC_FIELD = 5;
	public static class StaticInnerClass {
		public void instanceContext(int param<caret>) {
			int localVar = 0;
			int var1 = localVar + param + STATIC_FIELD;
		}
	}
}
