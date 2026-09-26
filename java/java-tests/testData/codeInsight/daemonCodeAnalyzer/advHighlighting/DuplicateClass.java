// duplicates in extends

class a {
}

class b extends a, <error descr="Duplicate reference to 'a' in 'extends' list">a</error> {
}

interface i {}

class c implements i, <error descr="Duplicate reference to 'i' in 'implements' list">i</error> {
}