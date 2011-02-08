import java.io.Serializable;

interface PublicCloneable {
	Object clone();
}
interface Extension extends Serializable, PublicCloneable {}
class Test {
	void foo(Extension bar) {
		bar.<ref>clone();
	}
}