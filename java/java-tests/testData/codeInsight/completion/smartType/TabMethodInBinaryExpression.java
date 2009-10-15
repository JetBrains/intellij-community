public class TabComplete {
	public void scene() {
		Servant servant = new Servant();
		int var = servant.met<caret>hod1() + 1;
	}
}

class Servant {
	public int method1() {
		return 1;
	}

}