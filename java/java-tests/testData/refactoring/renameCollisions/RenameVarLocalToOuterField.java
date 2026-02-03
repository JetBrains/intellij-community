public class RenameCollisions {
	private int myField = 7;
	class InnerClass {
		public void instanceContext(int param) {
			int localVar<caret> = 0;
			int var1 = localVar + param + myField;
		}
	}
}
