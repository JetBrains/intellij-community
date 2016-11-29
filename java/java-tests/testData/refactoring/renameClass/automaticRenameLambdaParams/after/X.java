
import java.util.ArrayList;
import java.util.List;

class Baz {}
class X {
	void test2() {
		List<Baz> bazs = new ArrayList<>();
		bazs.forEach(baz -> System.out.println(baz));
	}
}