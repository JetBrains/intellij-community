// LocalsOrMyInstanceFieldsControlFlowPolicy

import java.io.FileNotFoundException;
import java.io.FileReader;

class ExceptionTestCase {

	public void ensureAllInvalidateTasksCompleted(boolean a, boolean b, boolean c) {<caret>
		final boolean doProgressThing;
		doProgressThing = c && a && b;
	}
}