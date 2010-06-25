// "Add Catch Clause(s)" "true"

import java.io.FileNotFoundException;
import java.io.IOException;

class CatchExceptions {

	void foo() throws java.io.IOException, java.io.FileNotFoundException {

	}

	void bar() {
		try {
		foo();
		} catch (FileNotFoundException e) {
            <selection>e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.</selection>
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}