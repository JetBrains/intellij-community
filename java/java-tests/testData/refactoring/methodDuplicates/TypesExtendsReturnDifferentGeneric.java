import java.util.Collection;
import java.util.List;

class Types {
	public Collection<String> <caret>genericMethod() {
		return new ArrayList<String>();
	}
	public void genericContext() {
		List<String> sl = new ArrayList<String>();
		Collection<String> sc = genericMethod();
	}
}
