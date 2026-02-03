import java.util.AbstractSet;
import java.util.Set;

interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Set<Subject> myField;

	public void meth() {
		Set<Subject> ancestors = null;
		myField = ancestors;
		Set<Subject> ancestorExtends = null;
		myField = ancestorExtends;
		Set<Subject> ancestorSupers = null;
		myField = ancestorSupers;

		// turning everything into Set<Subject> is actually too strict, but correct
		Set<Subject> subjects = null;
		myField = subjects;
		Set<Subject> subjectExtends = null;
		myField = subjectExtends;
		Set<Subject> subjectSupers = null;
		myField = subjectSupers;

		Set<Subject> descendants = null;
		myField = descendants;
		Set<Subject> descendantExtends = null;
		myField = descendantExtends;
		Set<Subject> descendantSupers = null;
		myField = descendantSupers;

		Set set = null;
		myField = set;
		AbstractSet<Subject> myCollection = null;
		myField = myCollection;
	}
}
