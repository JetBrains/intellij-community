public class ConstructedClass {
    public static ConstructedClass PEC_ONE = new ConstructedClass("param");

    ConstructedClass(int field) {
    }

    public <caret>ConstructedClass(String keyword) {
	this(keyword.length());
    }
}