import java.util.Set;

class <caret>Subject<U, V> {
}

interface SubjectFace {
}

public class Client extends Subject<SubjectFace, Set<Object>> implements SubjectFace {
	private Subject<SubjectFace, Set<Object>> mySubject = new Subject<SubjectFace, Set<Object>>();
	private SubjectFace mySubjectFace = new SubjectFace() {
	};

	public Subject<SubjectFace, Set<Object>> subjectMethod(Subject<SubjectFace, Set<Object>> subject) {
		Subject<SubjectFace, Set<Object>> varSubject = new Subject<SubjectFace, Set<Object>>();
		return varSubject;
	}
	public SubjectFace subjectFaceMethod(SubjectFace subjectFace) {
		SubjectFace varSubjectFace = new SubjectFace() {
		};
		return varSubjectFace;
	}
}
