// "Add 'catch' clause(s)" "true"
class CatchExceptions {

	void foo() throws java.io.IOException, java.io.FileNotFoundException {

	}

	void bar() {
		try {
		fo<caret>o();
		}
	}
}