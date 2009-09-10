class <caret>Subject {
}

interface SubjectFace {
}

public class Client extends Subject implements SubjectFace {
	private Subject mySubject = new Subject();
	private SubjectFace mySubjectFace = new SubjectFace() {
	};

	public Subject subjectMethod(Subject subject) {
		Subject varSubject = new Subject();
		return varSubject;
	}
	public SubjectFace subjectFaceMethod(SubjectFace subjectFace) {
		SubjectFace varSubjectFace = new SubjectFace() {
		};
		return varSubjectFace;
	}
}
