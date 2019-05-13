import java.io.FileNotFoundException;
import java.io.IOException;

// "Add 'catch' clause(s)" "true"
class CatchExceptions {

	void foo() throws java.io.IOException, java.io.FileNotFoundException {

	}

	void bar() {
		try {
		foo();
		} catch (FileNotFoundException e) {
            <selection>e.printStackTrace();</selection>
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}