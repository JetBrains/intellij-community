public class RenameCollisions {
	class InnerClass {
		public static final int INNER_STATIC_FIELD = 13;
		public void instanceContext(int param) {
			int <caret>localVar = 0;
			int var1 = localVar + param + INNER_STATIC_FIELD;
		}
	}
}
