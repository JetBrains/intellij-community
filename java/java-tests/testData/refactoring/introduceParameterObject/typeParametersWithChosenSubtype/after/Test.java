import java.util.Collection;
import java.util.List;

public class Test<U extends List> {
	public Collection foo(Param param) {
		return param.getP();
	}

	public void context1(U p) {
		Collection v = foo(new Param(p));
	}

    private static class Param {
        private final Collection p;

        private Param(Collection p) {
            this.p = p;
        }

        public Collection getP() {
            return p;
        }
    }
}
