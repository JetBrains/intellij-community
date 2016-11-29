
import java.util.ArrayList;
import java.util.List;

class Bar {}
class X {
	void test2() {
		List<Bar> bars = new ArrayList<>();
		bars.forEach(bar -> System.out.println(bar));
	}
}