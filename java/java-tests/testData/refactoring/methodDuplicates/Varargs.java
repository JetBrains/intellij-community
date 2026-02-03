import java.util.*;

public class Varargs {
	private List<String> <caret>method(String... values) {
		return Arrays.asList(values);
	}
	private void context() {
		List<String> list1 = Arrays.asList("hi", "bye");
		List<String> list2 = Arrays.asList("hi");
		List<String> list3 = Arrays.asList(new String[] {});
		String[] sa = new String[] {};
		List<String> list4 = Arrays.asList(sa);

		List listA = Arrays.asList(0);
	}
}
