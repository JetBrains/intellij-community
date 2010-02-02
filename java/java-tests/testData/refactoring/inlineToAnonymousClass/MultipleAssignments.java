public class WithCtor {
	private int myInt = 8;
	private String myString = "Sashya";

	public WithCtor() {
	}

	public WithCtor(int anInt, String string) {
		int i = 2;
		i = 3;
		myInt = i + anInt;
		int j = 4;
		j = 5;
		myString = string.substring(j);
	}
}

class Usage {
  private WithCtor wc1 = new With<caret>Ctor(17, "Sa");
}