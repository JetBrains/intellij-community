public class DeclarationUsage {
	private int myField;
	public void met<caret>hod(int p) {
		int v = 0;
		myField += p;
	}
	public void context() {
		int v = 0;
		myField += v;
	}
}