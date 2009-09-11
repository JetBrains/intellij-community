public class RenameCollisions {
	class InnerClass {
		private int myInnerField = 15;
		public void instanceContext(int param<caret>) {
			int localVar = 0;
			int var1 = localVar + param + myInnerField;
		}
	}
}
