import static java.io.File.separatorChar;

public class RenameCollisions {
	public static class StaticInnerClass {
		public static void staticContext(int param<caret>) {
			int var1 = separatorChar;
		}
	}
}
