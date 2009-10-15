public class TabComplete {
	public void scene() {
		Servant servant = new Servant();
		int var = servant.method1()<caret> + 1;
	}
}

class Servant {
	public int method1() {
		return 1;
	}

}