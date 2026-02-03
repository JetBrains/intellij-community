public class TestCase extends Zzz {
	public boolean ttt(Zzz o) {
		return o instanceof TestCase<caret>
	}
}

class Zzz {
    private void fax() {}
}