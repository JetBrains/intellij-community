import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class Genmeth {
	public void context() {
		Integer v1 = 0;
		AbstractList<String> v2 = new ArrayList<String>(0);
		int res = v1.hashCode() + v2.size();
	}

	public <T, U extends List> int <caret>method(T t, U u) {
		return t.hashCode() + u.size();
	}
}
