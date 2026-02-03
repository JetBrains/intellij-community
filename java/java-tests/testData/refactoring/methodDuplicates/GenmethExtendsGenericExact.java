import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class Genmeth {
	public void context() {
		Integer v1 = 0;
		int v1a = v1.hashCode();
		AbstractList<String> v2 = new ArrayList<String>(0);
		int v2a = v2.hashCode();
		AbstractList<Double> v3 = new ArrayList<Double>(0);
		int v3a = v3.hashCode();
		AbstractList<Integer> v4 = new ArrayList<Integer>(0);
		int v4a = v4.hashCode();
	}

	public <T extends List<Integer>> int <caret>method(T t) {
		return t.hashCode();
	}
}
