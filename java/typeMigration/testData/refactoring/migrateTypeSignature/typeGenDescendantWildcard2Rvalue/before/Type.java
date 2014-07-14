import java.util.Set;

interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Set myField;

	public void meth() {
		Set<Ancestor> ancestors = null;
		myField = ancestors;
		Set<? extends Ancestor> ancestorExtends = null;
		myField = ancestorExtends;
		Set<? super Ancestor> ancestorSupers = null;
		myField = ancestorSupers;

		Set<Subject> subjects = null;
		myField = subjects;
		Set<? extends Subject> subjectExtends = null;
		myField = subjectExtends;
		Set<? super Subject> subjectSupers = null;
		myField = subjectSupers;

		Set<Descendant> descendants = null;
		myField = descendants;
		Set<? extends Descendant> descendantExtends = null;
		myField = descendantExtends;
		Set<? super Descendant> descendantSupers = null;
		myField = descendantSupers;

		Set set = null;
		myField = set;
	}
}
