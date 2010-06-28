// "Add Catch Clause(s)" "true"
class CatchExceptions {

	void foo() throws java.io.IOException, java.io.FileNotFoundException {

	}

	void bar() {
		try {
		foo();<caret>
		}
	}
}