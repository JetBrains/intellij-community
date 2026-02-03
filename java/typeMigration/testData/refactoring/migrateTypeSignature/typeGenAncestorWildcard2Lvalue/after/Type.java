import java.util.Set;

interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Set<? extends Subject> myAncestors;
	private Set<? extends Ancestor> myAncestorExtends;
	private Set<? extends Subject> myAncestorSupers;

	private Set<? extends Subject> mySubjects;
	private Set<? extends Subject> mySubjectExtends;
	private Set<? extends Subject> mySubjectSupers;

	private Set<? extends Subject> myDescendants;
	private Set<? extends Subject> myDescendantExtends;
	private Set<? extends Subject> myDescendantSupers;

	private Set mySet;

	public void meth(Set<? extends Subject> p) {
		myAncestors = p;
		myAncestorExtends = p;
		myAncestorSupers = p;

		mySubjects = p;
		mySubjectExtends = p;
		mySubjectSupers = p;

		myDescendants = p;
		myDescendantExtends = p;
		myDescendantSupers = p;

		mySet = p;
	}
}
