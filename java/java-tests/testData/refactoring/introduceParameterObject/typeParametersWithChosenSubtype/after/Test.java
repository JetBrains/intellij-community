import java.util.Collection;
import java.util.List;

public class Test<U extends List> {
	public Collection foo(Param param) {
		return param.p();
	}

	public void context1(U p) {
		Collection v = foo(new Param(p));
	}

    private static record Param(Collection p) {
    }
}
