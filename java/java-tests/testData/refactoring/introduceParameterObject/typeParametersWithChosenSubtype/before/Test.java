import java.util.Collection;
import java.util.List;

public class Test<U extends List> {
	public Collection foo(U p) {
		return p;
	}

	public void context1(U p) {
		Collection v = foo(p);
	}
}
