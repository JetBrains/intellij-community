class <caret>Subject<T> {
}

interface SubjectFace {
}

public class Client extends Subject<String> implements SubjectFace {
	private Subject<String> mySubject = new Subject<String>();
	private SubjectFace mySubjectFace = new SubjectFace() {
	};

	public Subject<String> subjectMethod(Subject<String> subject) {
		Subject<String> varSubject = new Subject<String>();
		return varSubject;
	}
	public SubjectFace subjectFaceMethod(SubjectFace subjectFace) {
		SubjectFace varSubjectFace = new SubjectFace() {
		};
		return varSubjectFace;
	}
}
