import org.intellij.lang.annotations.Language;

class Test {

	@Language("JAVA")
	String block = """
		class A{<caret>}
		""";

}