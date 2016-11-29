import static java.io.File.separatorChar;

public class RenameCollisions {
	public static class StaticInnerClass {
		public static void staticContext() {
			int localVar<caret> = 0;
			int var1 = separatorChar;
		}
	}
}
