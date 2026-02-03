@interface Ann {
}

@<error descr="Duplicate annotation">Ann</error> @<error descr="Duplicate annotation">Ann</error> class D {
}

@SuppressWarnings({})
@<error descr="Annotation type expected">java.lang</error>
class PsiDa {
}
