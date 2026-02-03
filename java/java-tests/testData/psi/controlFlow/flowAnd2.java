// LocalsOrMyInstanceFieldsControlFlowPolicy

class ExceptionTestCase {
	public String getIndexingLexer(final boolean file, boolean b1, boolean b2) {<caret>
		String highlighter;

		if (file && b1 && b2) {
			highlighter = "then";
		}
		else {
			highlighter = "else";
		}
		return highlighter;
	}
}